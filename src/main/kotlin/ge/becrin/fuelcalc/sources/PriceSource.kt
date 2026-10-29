package ge.becrin.fuelcalc.sources

import ge.becrin.fuelcalc.prices.PriceFrequency

enum class PriceSource(val frequency: PriceFrequency) {
    OFFICIAL(PriceFrequency.WEEKLY),
    MARKET(PriceFrequency.DAILY)
}
