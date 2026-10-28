package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.regions.dto.RegionRefDto

data class ComparisonSummaryDto(
    val status: ComparisonStatus,

    val snapshotId: String?,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val period: PricePeriodDto? = null,

    val basePriceRubPerLiter: String?,

    val lowestPriceRubPerLiter: String?,

    val lowestRegions: List<RegionRefDto>,

    val lowestDifferenceRubPerLiter: String?,

    val hasNewerObservations: Boolean
)
