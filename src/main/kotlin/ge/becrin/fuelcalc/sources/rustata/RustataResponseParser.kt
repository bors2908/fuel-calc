package ge.becrin.fuelcalc.sources.rustata

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.regions.model.Region
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.SourceImportException
import java.time.LocalDate
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class RustataResponseParser {
    fun parse(
        root: JsonNode,
        window: FetchWindow,
        expectedOffset: Int,
        regions: Map<String, Region>
    ): RustataPage {
        val fuels = FuelType.entries.associateBy { it.rosstatCode }
        val facts = mutableListOf<FetchedPrice>()

        require(root.path("series").isArray && root.path("observations").isArray) { "Invalid rustata response" }
        require(root.path("offset").asInt(-1) == expectedOffset) { "Unexpected rustata offset" }

        val pageTotal = root.path("series_total").asInt(-1)

        require(pageTotal >= 0) { "Rustata dataset changed during pagination" }
        require(root.path("series_count").asInt(-1) == root.path("series").size()) { "Invalid series count" }
        require(root.path("observation_count").asInt(-1) == root.path("observations").size()) { "Invalid observation count" }

        val series = root.path("series").associateBy { required(it, "series_key") }

        require(series.size == root.path("series").size()) { "Repeated rustata series" }

        for (observation in root.path("observations")) {
            val description = series.getValue(required(observation, "series_key"))

            require(required(description, "geo_level") == "region" && required(description, "unit") == "RUB")
            require(required(description, "indicator") == "AVG_PRICE" && required(description, "freq") == "W")
            require(description.path("transformation").asString("level") in listOf("level", ""))
            require(description.path("unit_mult").asString("1") in listOf("1", ""))

            val fuel = checkNotNull(fuels[required(description, "classification")]) { "Unknown rustata fuel" }
            val region = regions[required(description, "geo")] ?: continue
            val date = LocalDate.parse(required(observation, "period"))
            val price = observation.path("value").asString().toBigDecimalOrNull()

            require(date in window.from..window.until && price != null && price.signum() > 0) { "Invalid rustata observation" }

            val published = observation.path("as_of").takeUnless { it.isMissingNode || it.isNull }
                ?.asString()?.let(LocalDate::parse)

            facts.add(FetchedPrice(region.okatoCode, fuel, date, price, published))
        }

        require(root.path("has_more").isBoolean) { "Missing rustata pagination completeness marker" }

        val next = if (root.path("has_more").asBoolean()) root.path("next_offset").asInt(-1) else null

        require(next == null || (next == expectedOffset + series.size && next > expectedOffset)) {
            "Rustata pagination made no progress"
        }

        return RustataPage(pageTotal, series.keys, facts, next)
    }

    private fun required(
        node: JsonNode,
        name: String
    ): String =
        node.path(name).asString("").takeIf { it.isNotBlank() }
            ?: throw SourceImportException("INVALID_RESPONSE", "Missing rustata field [field=$name]")
}
