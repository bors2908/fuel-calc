package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.service.SourceImportException
import java.math.BigDecimal
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class BenzupResponseParser {
    fun parse(root: JsonNode): List<BenzupPrice> {
        if (root.path("error").asInt(-1) != 0 || !root.path("result").isArray) {
            throw SourceImportException("INVALID_RESPONSE", "BenzUp returned an error or an invalid regional index response")
        }

        val prices = mutableMapOf<Pair<String, FuelType>, BenzupPrice>()

        for (row in root.path("result")) {
            val fuel = FUELS[row.path("product").asString()] ?: continue
            val raw = row.path("index")

            if (raw.isNull || raw.isMissingNode || raw.asString().isBlank()) {
                continue
            }

            val regionId = row.path("region_id").asString()
            val price = raw.asString().toBigDecimalOrNull()

            if (!regionId.matches(Regex("[1-9][0-9]{0,9}")) ||
                price == null || price <= BigDecimal.ZERO || price.toPlainString().length > 64
            ) {
                throw SourceImportException("INVALID_RESPONSE", "Invalid BenzUp regional index [regionId=$regionId, fuel=$fuel]")
            }

            val entry = BenzupPrice(regionId, fuel, price)
            val previous = prices[regionId to fuel]

            if (previous?.let { it.price.compareTo(price) != 0 } == true) {
                throw SourceImportException("SOURCE_DATA_CONFLICT", "Conflicting BenzUp regional index [regionId=$regionId, fuel=$fuel]")
            }

            prices[regionId to fuel] = entry
        }

        if (prices.isEmpty()) {
            throw SourceImportException("EMPTY_PUBLICATION", "BenzUp returned no supported regional prices")
        }

        return prices.values.sortedWith(compareBy({ it.regionId.toLong() }, { it.fuel }))
    }

    companion object {
        private val FUELS = FuelType.entries.associate { it.benzupCode to it }
    }
}
