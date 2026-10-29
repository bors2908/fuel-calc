package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.prices.ComparisonStatus

data class ComparisonSnapshot(
    val selection: PriceSelection,

    val status: ComparisonStatus,

    val rows: List<PriceRow>,

    val period: PricePeriod?,

    val hasNewerObservations: Boolean
)
