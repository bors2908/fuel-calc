package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import java.net.URI
import org.springframework.stereotype.Component

@Component
class RosstatClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val catalog: RegionCatalog,

    private val report: RosstatReport
) {
    fun prepare(): RosstatReportDefinition {
        // The report API requires the builder's CSRF session; the page body is discarded.
        http.read(endpoint("/olap2/descr/report/${properties.rosstat.indicatorId}/")) { }

        val config = http.json(endpoint("/olap2/olap2-descriptor-report/${properties.rosstat.indicatorId}/"))
        val territories = http.json(URI.create(properties.regions.okatoEndpoint))
        val goods = http.json(endpoint("/backbone/set/${properties.rosstat.goodsSetId}"))

        return report.prepare(config, territories, goods, catalog.regions.map { it.okatoCode }.toSet())
    }

    fun fetch(
        window: FetchWindow,
        definition: RosstatReportDefinition
    ): List<FetchedPrice> {
        val form = report.request(definition, window) ?: return emptyList()
        val response = http.json(endpoint("/olap2/olap2-preview/"), form)

        return report.parse(response, definition, window)
    }

    private fun endpoint(path: String): URI =
        URI.create(properties.rosstat.baseUrl.trimEnd('/') + path)
}
