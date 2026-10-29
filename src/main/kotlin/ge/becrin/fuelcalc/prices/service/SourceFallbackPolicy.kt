package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.sources.PriceSource
import org.springframework.stereotype.Component

@Component
class SourceFallbackPolicy {
    fun order(preferred: PriceSource): List<PriceSource> =
        when (preferred) {
            PriceSource.ROSSTAT -> listOf(PriceSource.ROSSTAT, PriceSource.RUSTATA, PriceSource.BENZUP)
            PriceSource.RUSTATA -> listOf(PriceSource.RUSTATA, PriceSource.ROSSTAT, PriceSource.BENZUP)
            PriceSource.BENZUP -> listOf(PriceSource.BENZUP, PriceSource.RUSTATA, PriceSource.ROSSTAT)
        }
}
