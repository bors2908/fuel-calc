package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.SourceJson
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

@Component
class RosstatReport(sourceJson: SourceJson) {
    private val json = sourceJson.mapper

    fun prepare(
        config: JsonNode,
        territories: JsonNode,
        goods: JsonNode,
        selectedCodes: Set<String>
    ): RosstatReportDefinition {
        require(config.isObject && territories.isArray && goods.isArray) { "Invalid Rosstat descriptor or dictionaries" }

        val regionById = territories.filter { it.path("-17006").asString() in selectedCodes }
            .associate { it.path("id").asLong() to it.path("-17006").asString() }
        val fuelById = FuelType.entries.associate { fuel ->
            val matches = goods.filter { it.path("-17006").asString() == fuel.rosstatCode }

            require(matches.size == 1) { "Missing or ambiguous Rosstat fuel code [code=${fuel.rosstatCode}]" }

            matches.single().path("id").asLong() to fuel
        }

        require(regionById.values.toSet() == selectedCodes) { "Selected territories absent from Rosstat dictionary" }

        return RosstatReportDefinition(config as ObjectNode, regionById, fuelById)
    }

    fun request(
        definition: RosstatReportDefinition,
        window: FetchWindow
    ): Map<String, String>? {
        val config = definition.config.deepCopy()
        val regionById = definition.regions
        val fuelById = definition.fuels
        val dates = mutableListOf<String>()
        var date = window.from.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

        while (date <= window.until) {
            dates.add("$date 00:00:00|-58")

            date = date.plusWeeks(1)
        }

        if (dates.isEmpty()) {
            return null
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

        return mapOf("jkwargs" to json.writeValueAsString(config), "user_conf" to "{}")
    }

    fun parse(
        response: JsonNode,
        definition: RosstatReportDefinition,
        window: FetchWindow
    ): List<FetchedPrice> {
        val regionById = definition.regions
        val fuelById = definition.fuels
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
