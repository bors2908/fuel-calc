package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.regions.dto.RegionRefDto

data class ComparisonSummaryDto(
    val status: ComparisonStatus,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val period: PricePeriodDto? = null,

    val lowestPriceRubPerLiter: String?,

    val lowestRegions: List<RegionRefDto>,

    val hasNewerObservations: Boolean
)
