package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.sources.PriceSource
import org.springframework.stereotype.Component

@Component
class SourceFallbackPolicy {
    fun order(preferred: PriceSource): List<PriceSource> =
        when (preferred) {
            PriceSource.ROSSTAT -> listOf(PriceSource.ROSSTAT, PriceSource.RUSTATA, PriceSource.WHEREFUEL)
            PriceSource.RUSTATA -> listOf(PriceSource.RUSTATA, PriceSource.ROSSTAT, PriceSource.WHEREFUEL)
            PriceSource.WHEREFUEL -> listOf(PriceSource.WHEREFUEL, PriceSource.RUSTATA, PriceSource.ROSSTAT)
        }
}
