package ge.becrin.fuelcalc.sources.rustata

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDate

@Component
class RustataClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val regions: RegionCatalog
) {
    private val json = JsonMapper.builder().build()

    private var lastRequestNanos: Long = 0

    fun fetch(window: FetchWindow): List<FetchedPrice> {
        if (properties.rustata.token.isBlank()) {
            throw SourceImportException("TOKEN_REQUIRED", "Configure RUSTATA_TOKEN for the official fallback")
        }

        val fuels = FuelType.entries.associateBy { it.rosstatCode }
        val facts = mutableListOf<FetchedPrice>()
        val seenSeries = mutableSetOf<String>()
        var offset = 0
        var total: Int? = null

        for (page in 1..properties.rustata.maxPages) {
            val uri = UriComponentsBuilder.fromUriString(properties.rustata.baseUrl).path("/api/v1/data")
                .queryParam("dataset", properties.rustata.dataset).queryParam("geo", "region")
                .queryParam("class", fuels.keys.joinToString(",")).queryParam("unit", "RUB")
                .queryParam("freq", "W").queryParam("labels", "1")
                .queryParam("start", window.from).queryParam("end", window.until)
                .queryParam("limit", properties.rustata.pageSize).queryParam("offset", offset).build().toUri()
            val root = http.read(uri, mapOf("Authorization" to "Bearer ${properties.rustata.token}"), beforeAttempt = ::pace) {
                json.readTree(it.toFile())
            }

            require(root.path("series").isArray && root.path("observations").isArray) { "Invalid rustata response" }
            require(root.path("offset").asInt(-1) == offset) { "Unexpected rustata offset" }

            val pageTotal = root.path("series_total").asInt(-1)

            require(pageTotal >= 0 && (total == null || pageTotal == total)) { "Rustata dataset changed during pagination" }
            require(root.path("series_count").asInt(-1) == root.path("series").size()) { "Invalid series count" }
            require(root.path("observation_count").asInt(-1) == root.path("observations").size()) { "Invalid observation count" }

            total = pageTotal

            val series = root.path("series").associateBy { required(it, "series_key") }

            require(series.size == root.path("series").size() && series.keys.none { it in seenSeries }) { "Repeated rustata series" }

            seenSeries.addAll(series.keys)

            for (observation in root.path("observations")) {
                val description = series.getValue(required(observation, "series_key"))

                require(required(description, "geo_level") == "region" && required(description, "unit") == "RUB")
                require(required(description, "indicator") == "AVG_PRICE" && required(description, "freq") == "W")
                require(description.path("transformation").asString("level") in listOf("level", ""))
                require(description.path("unit_mult").asString("1") in listOf("1", ""))

                val fuel = checkNotNull(fuels[required(description, "classification")]) { "Unknown rustata fuel" }
                val region = regions.byOkatoCode(required(description, "geo")) ?: continue
                val date = LocalDate.parse(required(observation, "period"))
                val price = observation.path("value").asString().toBigDecimalOrNull()

                require(date in window.from..window.until && price != null && price.signum() > 0) { "Invalid rustata observation" }

                val published = observation.path("as_of").takeUnless { it.isMissingNode || it.isNull }
                    ?.asString()?.let(LocalDate::parse)

                facts.add(FetchedPrice(region.okatoCode, fuel, date, price, published))
            }

            require(root.path("has_more").isBoolean) { "Missing rustata pagination completeness marker" }

            if (!root.path("has_more").asBoolean()) {
                require(seenSeries.size == total) { "Incomplete rustata series set" }

                return facts
            }

            val next = root.path("next_offset").asInt(-1)

            require(next == offset + series.size && next > offset) { "Rustata pagination made no progress" }

            offset = next
        }

        throw SourceImportException("PAGE_LIMIT", "Rustata page limit exceeded")
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
            ?: throw SourceImportException("INVALID_RESPONSE", "Missing rustata field [field=$name]")
}
