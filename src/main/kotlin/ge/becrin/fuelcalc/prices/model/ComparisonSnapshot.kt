package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto

data class ComparisonSnapshot(
    val selection: PriceSelection,

    val status: ComparisonStatus,

    val rows: List<PriceRow>,

    val period: PricePeriodDto?,

    val snapshotId: String?,

    val hasNewerObservations: Boolean
)
