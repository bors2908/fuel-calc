package ge.becrin.fuelcalc.sources.rustata

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.RegionalSourceAdapter
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth

@Component
class RustataClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val regions: RegionCatalog,

    private val weeks: RustataWeekResolver
) : RegionalSourceAdapter {
    override val provider: String = "RUSTATA"

    override val enabled: Boolean
        get() = properties.rustata.enabled

    private val json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()

    private var lastRequestNanos: Long = 0

    override fun beginRun() {
        http.beginRun()
    }

    override fun definition(frequency: PriceFrequency): FeedDefinition =
        FeedDefinition("RUSTATA_${frequency}_V1", provider, "ROSSTAT", frequency, dataset(frequency))

    override fun fetch(
        feed: FeedDefinition,
        window: FetchWindow
    ): List<PreparedPublication> {
        if (properties.rustata.token.isBlank()) {
            throw SourceImportException("TOKEN_REQUIRED", "Configure RUSTATA_TOKEN to enable authenticated data access")
        }

        val factsByDate = mutableMapOf<LocalDate, MutableList<RegionalPriceFact>>()
        val seenSeries = mutableSetOf<String>()
        var offset = 0
        var total: Int? = null
        var complete = false

        for (page in 1..properties.rustata.maxPages) {
            val uri = UriComponentsBuilder.fromUriString(properties.rustata.baseUrl).path("/api/v1/data")
                .queryParam("dataset", feed.externalCode).queryParam("geo", "region")
                .queryParam("class", "7802,7803,7804").queryParam("unit", "RUB")
                .queryParam("freq", if (feed.frequency == PriceFrequency.WEEKLY) "W" else "M")
                .queryParam("labels", "1").queryParam("start", window.from).queryParam("end", window.until)
                .queryParam("limit", properties.rustata.pageSize).queryParam("offset", offset).build().toUri()
            val root = http.read(uri, mapOf("Authorization" to "Bearer ${properties.rustata.token}"), beforeAttempt = ::pace) {
                json.readTree(it.toFile())
            }
            require(root.path("series").isArray && root.path("observations").isArray) { "Invalid rustata response" }
            require(root.path("offset").asInt(-1) == offset) { "Unexpected page offset" }
            val pageTotal = root.path("series_total").asInt(-1)
            require(pageTotal >= 0 && (total == null || pageTotal == total)) { "Dataset changed during pagination" }
            total = pageTotal
            require(root.path("series_count").asInt(-1) == root.path("series").size())
            require(root.path("observation_count").asInt(-1) == root.path("observations").size())

            log.info(
                "Rustata page received [feedCode={}, mode={}, from={}, until={}, page={}, offset={}, series={}, " +
                    "totalSeries={}, observations={}, hasMore={}]",
                feed.code, window.mode, window.from, window.until, page, offset, root.path("series_count").asInt(),
                total, root.path("observation_count").asInt(), root.path("has_more").asString()
            )

            val series = root.path("series").associateBy { required(it, "series_key") }
            require(series.size == root.path("series").size()) { "Duplicate series key" }
            require(series.keys.none { it in seenSeries }) { "Repeated series across pages" }
            seenSeries.addAll(series.keys)

            for (observation in root.path("observations")) {
                val description = series[required(observation, "series_key")]
                    ?: throw SourceImportException("UNKNOWN_SERIES", "Observation refers to an unknown series")
                require(required(description, "geo_level") == "region")
                require(required(description, "unit") == "RUB")
                require(required(description, "indicator") == "AVG_PRICE")
                require(required(description, "freq") == if (feed.frequency == PriceFrequency.WEEKLY) "W" else "M")
                val fuel = FUELS[required(description, "classification")]
                    ?: throw SourceImportException("UNKNOWN_FUEL", "Unexpected rustata fuel code")
                val label = required(description, "class_label")
                require(label.contains(", л") && label.contains(
                    if (fuel == "DIESEL") "Дизельное топливо" else if (fuel == "AI_92") "АИ-92" else "АИ-95"
                )) { "Fuel label or volume does not match" }
                val region = regions.byCode(required(description, "geo"))
                    ?: throw SourceImportException("UNKNOWN_REGION", "Unmapped rustata territory")
                require(regions.byName(required(description, "geo_label"))?.code == region.code) { "Territory code/name mismatch" }
                val transformation = description.path("transformation")
                require(transformation.isMissingNode || transformation.isNull || transformation.asString() == "level")
                val multiplier = description.path("multiplier")
                require(multiplier.isMissingNode || multiplier.isNull || multiplier.asString() == "1")
                val sourceDate = LocalDate.parse(required(observation, "period"))
                require(feed.frequency != PriceFrequency.MONTHLY || sourceDate.dayOfMonth == 1)

                val date =
                    if (feed.frequency == PriceFrequency.MONTHLY) YearMonth.from(sourceDate).atEndOfMonth()
                    else sourceDate
                require(date in window.from..window.until) { "Observation outside requested window" }
                val value = observation.path("value")
                require(value.isNumber) { "Non-numeric rustata value" }
                val asOf = observation.path("as_of").takeUnless { it.isMissingNode || it.isNull }?.asString()?.let(LocalDate::parse)
                val fact = RegionalPriceFact(region.code, fuel, value.decimalValue(), asOf)

                factsByDate.getOrPut(date) { mutableListOf() }.add(fact)
            }

            if (!root.path("has_more").isBoolean) {
                throw SourceImportException("INVALID_PAGINATION", "Missing pagination completeness marker")
            }

            if (!root.path("has_more").asBoolean()) {
                require(seenSeries.size == total) { "Incomplete series set" }
                complete = true

                break
            }

            val next = root.path("next_offset").asInt(-1)
            require(next == offset + series.size && next > offset) { "Pagination made no progress" }
            offset = next
        }

        if (!complete) {
            throw SourceImportException("PAGE_LIMIT", "Rustata page limit exceeded")
        }

        val calendar =
            if (feed.frequency == PriceFrequency.WEEKLY && factsByDate.isNotEmpty()) {
                weeks.resolve(feed, window, factsByDate)
            } else {
                emptyMap()
            }

        return factsByDate.toSortedMap().map { (date, facts) ->
            val period =
                if (feed.frequency == PriceFrequency.MONTHLY) {
                    PricePeriod(feed.frequency, date, date.withDayOfMonth(1), date)
                } else {
                    checkNotNull(calendar[date])
                }

            PreparedPublication(
                period, "${feed.externalCode}:$date",
                "${properties.rustata.baseUrl}/indicator/${feed.externalCode}",
                "Росстат через rustata · средние потребительские цены, ₽/л" +
                    if (period.referenceDate != date) " · исходная метка rustata: $date" else "", facts
            )
        }
    }

    private fun pace() {
        val wait = properties.rustata.requestInterval.toNanos() - (System.nanoTime() - lastRequestNanos)

        if (lastRequestNanos != 0L && wait > 0) {
            Thread.sleep(Duration.ofNanos(wait))
        }

        lastRequestNanos = System.nanoTime()
    }

    private fun required(
        node: JsonNode,
        name: String
    ): String =
        node.path(name).asString("").takeIf { it.isNotBlank() }
            ?: throw SourceImportException("INVALID_FORMAT", "Missing rustata field: $name")

    private fun dataset(frequency: PriceFrequency): String =
        if (frequency == PriceFrequency.WEEKLY) "fedstat/indicator_37426" else "fedstat/indicator_31448"

    companion object {
        private val log = LoggerFactory.getLogger(RustataClient::class.java)

        private val FUELS = mapOf("7802" to "AI_92", "7803" to "AI_95", "7804" to "DIESEL")
    }
}
