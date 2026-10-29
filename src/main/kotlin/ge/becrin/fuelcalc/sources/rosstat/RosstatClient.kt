package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Component
class RosstatClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val catalog: RegionCatalog
) {
    private val json = JsonMapper.builder().build()

    fun fetch(window: FetchWindow): List<FetchedPrice> {
        // The public report API uses the same CSRF session as the report builder; its page body is discarded.
        http.read(endpoint("/olap2/descr/report/${properties.rosstat.indicatorId}/")) { }

        val config = http.json(endpoint("/olap2/olap2-descriptor-report/${properties.rosstat.indicatorId}/")) as ObjectNode
        val territories = http.json(URI.create(properties.regions.okatoEndpoint))
        val goods = http.json(endpoint("/backbone/set/${properties.rosstat.goodsSetId}"))

        require(territories.isArray && goods.isArray) { "Invalid Rosstat dictionaries" }

        val selectedCodes = catalog.regions.map { it.okatoCode }.toSet()
        val regionById = territories.filter { it.path("-17006").asString() in selectedCodes }
            .associate { it.path("id").asLong() to it.path("-17006").asString() }
        val fuelById = FuelType.entries.associate { fuel ->
            val matches = goods.filter { it.path("-17006").asString() == fuel.rosstatCode }

            require(matches.size == 1) { "Missing or ambiguous Rosstat fuel code [code=${fuel.rosstatCode}]" }

            matches.single().path("id").asLong() to fuel
        }

        require(regionById.values.toSet() == selectedCodes) { "Selected territories absent from Rosstat dictionary" }

        val dates = mutableListOf<String>()
        var date = window.from.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

        while (date <= window.until) {
            dates.add("$date 00:00:00|-58")

            date = date.plusWeeks(1)
        }

        if (dates.isEmpty()) {
            return emptyList()
        }

        configure(config.path("row_header"), 13033, regionById.keys.toList())
        configure(config.path("row_header"), 108930, fuelById.keys.toList())
        configure(config.path("col_header"), -64, dates)
        normalize(config.path("row_header"))
        normalize(config.path("col_header"))
        normalize(config.path("filter"))
        config.put("headers_url", "/olap2/olap2-preview/headers")
        config.put("data_url", "/olap2/olap2-preview/data")
        config.put("preview_url", "/olap2/olap2-preview/")
        config.put("repo_registry", true)

        val response = http.json(
            endpoint("/olap2/olap2-preview/"),
            mapOf("jkwargs" to json.writeValueAsString(config), "user_conf" to "{}")
        )
        val rows = response.path("headers").path("rowFilters")
        val columns = response.path("headers").path("colFilters")
        val data = response.path("data").path("reportData").path("data")

        require(rows.isArray && columns.isArray && data.isArray && rows.size() == data.size()) {
            "Invalid Rosstat report dimensions"
        }

        val result = mutableListOf<FetchedPrice>()

        for ((rowIndex, filters) in rows.withIndex()) {
            val region = regionById.getValue(filter(filters, "13033").toLong())
            val fuel = fuelById.getValue(filter(filters, "108930").toLong())
            val values = data.path(rowIndex)

            require(values.isArray && values.size() == columns.size()) { "Invalid Rosstat report row" }

            for ((columnIndex, column) in columns.withIndex()) {
                val referenceDate = LocalDate.parse(filter(column, "-64").substringBefore(' '))
                val value = values.path(columnIndex).path("db_value")

                require(referenceDate in window.from..window.until) { "Rosstat returned a period outside requested window" }

                if (!value.isMissingNode && !value.isNull) {
                    val price = value.asString().toBigDecimalOrNull()

                    require(price != null && price.signum() > 0) { "Invalid Rosstat price" }

                    result.add(FetchedPrice(region, fuel, referenceDate, price))
                }
            }
        }

        return result
    }

    private fun endpoint(path: String): URI =
        URI.create(properties.rosstat.baseUrl.trimEnd('/') + path)

    private fun filter(
        filters: JsonNode,
        descriptor: String
    ): String =
        filters.toList().map { it.asString() }.single { it.startsWith("$descriptor#eq#") }.substringAfter("#eq#")

    private fun configure(
        nodes: JsonNode,
        descriptor: Long,
        values: List<Any>
    ) {
        var found = false

        fun visit(items: JsonNode) {
            for (item in items) {
                if (item.path("descriptor").asLong() == descriptor) {
                    (item as ObjectNode).set("values", json.valueToTree(values))
                    found = true
                }

                visit(item.path("children"))
            }
        }

        visit(nodes)

        require(found) { "Required Rosstat dimension absent [descriptor=$descriptor]" }
    }

    private fun normalize(nodes: JsonNode) {
        for (node in nodes) {
            val descriptor = node.path("descriptor")

            if (descriptor.isNumber) {
                (node as ObjectNode).set("descriptor", json.createArrayNode().add(descriptor.asLong()))
            }

            normalize(node.path("children"))
        }
    }
}
