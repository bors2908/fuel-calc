package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.regions.model.Region
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class RosstatRegionParser {
    fun parse(
        full: JsonNode,
        selection: JsonNode
    ): List<Region> {
        require(full.isArray && selection.isArray && !selection.isEmpty) { "Invalid OKATO catalog or selection" }

        val byId = full.associateBy { it.path("id").asLong() }
        val ids = selection.toList().map { it.path("-298").asLong() }

        require(byId.size == full.size() && ids.distinct().size == ids.size && ids.all { it in byId }) {
            "Invalid or unresolved OKATO selection references"
        }

        val selected = ids.map { id ->
            val row = byId.getValue(id)

            Region(row.path("-17006").asString(""), regionName(row.path("-17007").asString("")))
        }.sortedBy { it.okatoCode }

        require(selected.all { it.okatoCode.isNotBlank() && it.name.isNotBlank() }) { "Invalid selected territory" }
        require(selected.map { it.okatoCode }.distinct().size == selected.size) { "Duplicate selected OKATO code" }

        return selected
    }

    private fun regionName(name: String): String {
        if (!name.contains("город федерального значения", ignoreCase = true)) {
            return name
        }

        return WHITESPACE.replace(FEDERAL_CITY_DESCRIPTIONS.replace(name, ""), " ").trim()
    }

    companion object {
        private val FEDERAL_CITY_DESCRIPTIONS = Regex(
            "(?U)\\b(?:город федерального значения|столица российской федерации|город)\\b",
            RegexOption.IGNORE_CASE
        )

        private val WHITESPACE = Regex("(?U)\\s+")
    }
}
