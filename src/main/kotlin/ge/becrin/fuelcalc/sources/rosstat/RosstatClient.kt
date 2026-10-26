package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.service.RegionalSourceAdapter
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class RosstatClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val weekly: RosstatWeeklyParser,

    private val monthly: RosstatMonthlyParser,

    private val documents: RosstatCatalogParser
) : RegionalSourceAdapter {
    override val provider: String = "ROSSTAT"

    override val enabled: Boolean
        get() = properties.rosstat.enabled

    private val parsedFiles = mutableMapOf<String, List<PreparedPublication>>()

    override fun beginRun() {
        http.beginRun()
        parsedFiles.clear()
    }

    override fun definition(frequency: PriceFrequency): FeedDefinition =
        FeedDefinition(
            "ROSSTAT_${frequency}_V1", provider, provider, frequency,
            if (frequency == PriceFrequency.WEEKLY) properties.rosstat.weeklyCatalog else properties.rosstat.monthlyCatalog
        )

    override fun fetch(
        feed: FeedDefinition,
        window: FetchWindow
    ): List<PreparedPublication> {
        val catalogUri = URI.create(feed.externalCode)
        val catalog = http.read(catalogUri) { Jsoup.parse(it.toFile(), null, catalogUri.toString()) }
        val links = documents.links(catalog, feed.frequency)

        if (links.isEmpty()) {
            throw SourceImportException(
                "EMPTY_CATALOG", "No compatible Rosstat documents found [frequency=${feed.frequency}, pageTitle=${catalog.title()}]"
            )
        }

        val selected = links.filter { link ->
            val text = link.title
            val years = Regex("20\\d{2}").findAll(text).map { it.value.toInt() }.toList()
            years.any { it in window.from.year..window.until.year } &&
                (feed.frequency != PriceFrequency.WEEKLY ||
                    runCatching { weekly.period(text).referenceDate in window.from..window.until }.getOrDefault(true))
        }
        val result = mutableListOf<PreparedPublication>()

        for (link in selected) {
            val uri = link.uri
            require(uri.host == catalogUri.host) { "Rosstat link points outside configured host" }
            val published = Regex("(\\d{2})[-.](\\d{2})[-.](20\\d{2})").find(uri.path)?.value
                ?.replace('-', '.')?.let { LocalDate.parse(it, PUBLICATION_DATE) }
            val publications = parsedFiles.getOrPut(uri.toString()) {
                http.read(uri) { path ->
                    if (feed.frequency == PriceFrequency.WEEKLY) {
                        listOf(weekly.parse(Jsoup.parse(path.toFile(), null, uri.toString()), uri.toString(), published))
                    } else {
                        monthly.parse(path, uri.toString(), null)
                    }
                }
            }

            result.addAll(publications.filter { it.period.referenceDate in window.from..window.until })
        }

        if (selected.isEmpty()) {
            throw SourceImportException("MISSING_HISTORY", "Rosstat catalog does not cover the requested years")
        }

        return result
    }

    companion object {
        private val PUBLICATION_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
    }
}
