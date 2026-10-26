package ge.becrin.fuelcalc.sources.wherefuel

import ge.becrin.fuelcalc.sources.model.RegionalDailySample
import ge.becrin.fuelcalc.sources.service.ImportHash
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Component
class RegionalStationSampler {
    fun sample(
        regionCode: String,
        prices: List<StationPrice>,
        collectedAt: Instant
    ): List<RegionalDailySample> {
        val deduplicated = prices.groupBy { it.stationId to it.fuelCode }.values.map { versions ->
            val latest = versions.maxOf { it.observedAt }
            val candidates = versions.filter { it.observedAt == latest }

            if (candidates.map { it.price.stripTrailingZeros() }.distinct().size > 1) {
                throw SourceImportException("SOURCE_DATA_CONFLICT", "Conflicting station price at the same timestamp")
            }

            candidates.first()
        }

        return deduplicated.filter {
            it.price.signum() >= 0 && it.price.toPlainString().length <= 64 &&
                !it.observedAt.isAfter(collectedAt) && !it.observedAt.isBefore(collectedAt.minusSeconds(7 * 86400))
        }.groupBy { it.fuelCode }.mapNotNull { (fuel, candidates) ->
            val selected = candidates.sortedWith(compareByDescending<StationPrice> { it.observedAt }.thenBy { it.stationId }).take(20)

            if (selected.size < 5) {
                null
            } else {
                val mean = selected.fold(BigDecimal.ZERO) { sum, value -> sum + value.price }
                    .divide(selected.size.toBigDecimal(), 6, RoundingMode.HALF_UP)
                val hash = ImportHash.hash(listOf(POLICY, regionCode, fuel) + selected.flatMap {
                    listOf(it.stationId, it.price, it.observedAt)
                })

                RegionalDailySample(
                    regionCode, fuel, mean, selected.size, collectedAt,
                    selected.minOf { it.observedAt }, selected.maxOf { it.observedAt }, hash
                )
            }
        }
    }

    companion object {
        const val POLICY = "TOP20_FRESH7D_MIN5_V1"
    }
}
