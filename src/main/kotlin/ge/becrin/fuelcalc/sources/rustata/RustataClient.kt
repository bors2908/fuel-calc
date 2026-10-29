package ge.becrin.fuelcalc.sources.rustata

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import ge.becrin.fuelcalc.sources.service.SourceJson
import java.time.Duration
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class RustataClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val regions: RegionCatalog,

    private val parser: RustataResponseParser,

    sourceJson: SourceJson
) {
    private val json = sourceJson.mapper

    private var lastRequestNanos: Long = 0

    fun fetch(window: FetchWindow): List<FetchedPrice> {
        if (properties.rustata.token.isBlank()) {
            throw SourceImportException("TOKEN_REQUIRED", "Configure RUSTATA_TOKEN for the official fallback")
        }

        val fuels = FuelType.entries.associateBy { it.rosstatCode }
        val selectedRegions = regions.regions.associateBy { it.okatoCode }
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

            val parsed = parser.parse(root, window, offset, selectedRegions)

            require(total == null || parsed.total == total) { "Rustata dataset changed during pagination" }
            require(parsed.series.none { it in seenSeries }) { "Repeated rustata series" }

            total = parsed.total
            seenSeries.addAll(parsed.series)
            facts.addAll(parsed.facts)

            val next = parsed.nextOffset

            if (next == null) {
                require(seenSeries.size == total) { "Incomplete rustata series set" }

                return facts
            }

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
}
