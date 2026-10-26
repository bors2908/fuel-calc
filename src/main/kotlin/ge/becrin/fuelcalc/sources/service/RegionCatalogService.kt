package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.rosstat.RosstatRegionCatalogParser
import ge.becrin.fuelcalc.sources.rosstat.RosstatCatalogParser
import ge.becrin.fuelcalc.prices.PriceFrequency
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI

@Service
class RegionCatalogService(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val parser: RosstatRegionCatalogParser,

    private val store: ImportStore,

    private val catalog: RegionCatalog,

    private val documents: RosstatCatalogParser
) {
    fun restore() {
        val saved = store.savedRegions()

        if (saved.isNotEmpty()) {
            catalog.replace(saved)

            log.info("Region catalog restored from database [regionCount={}]", saved.size)
        }
    }

    fun refresh(): Boolean {
        var stage = "CATALOG_DOWNLOAD"
        var url = properties.rosstat.monthlyCatalog

        try {
            http.beginRun()

            val catalogUri = URI.create(properties.rosstat.monthlyCatalog)
            val document = http.read(catalogUri) { Jsoup.parse(it.toFile(), null, catalogUri.toString()) }
            val link = documents.links(document, PriceFrequency.MONTHLY)
                .filter { Regex("20\\d{2}").containsMatchIn(it.title) }
                .maxByOrNull { link -> Regex("20\\d{2}").findAll(link.title).maxOf { it.value.toInt() } }
                ?: throw SourceImportException(
                    "EMPTY_CATALOG", "No current Rosstat workbook found for region catalog [pageTitle=${document.title()}]"
                )
            val uri = link.uri

            require(uri.host == catalogUri.host) { "Rosstat link points outside configured host" }

            stage = "WORKBOOK_DOWNLOAD"
            url = uri.toString()

            log.info("Region catalog workbook selected [url={}]", url)

            val regions = http.read(uri, reader = parser::parse)
            val verified = RegionCatalog()
            verified.replace(regions)

            stage = "DATABASE_WRITE"

            store.synchronizeRegions(verified.regions)
            restore()

            log.info("Region catalog refreshed [regionCount={}]", catalog.regions.size)
        } catch (error: Exception) {
            val code = (error as? SourceImportException)?.code ?: error.javaClass.simpleName

            log.warn(
                "Region catalog refresh failed [stage={}, url={}, errorCode={}, savedCatalogAvailable={}, message={}]",
                stage, url, code, catalog.isReady, error.message, error
            )
        }

        return catalog.isReady
    }

    companion object {
        private val log = LoggerFactory.getLogger(RegionCatalogService::class.java)
    }
}
