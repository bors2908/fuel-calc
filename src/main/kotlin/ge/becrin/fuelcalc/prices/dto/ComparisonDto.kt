package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.api.SortOrder
import ge.becrin.fuelcalc.prices.ComparisonSortBy
import ge.becrin.fuelcalc.prices.ComparisonStatus

data class ComparisonDto<T>(
    val status: ComparisonStatus,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val period: PricePeriodDto? = null,

    val sortBy: ComparisonSortBy,

    val sortOrder: SortOrder,

    val data: List<T>,

    val allPricesEqual: Boolean?,

    val hasNewerObservations: Boolean
)
