package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.SortOrder
import ge.becrin.fuelcalc.prices.ComparisonSortBy

class FuelPriceComparisonQueryDto : FuelPriceQueryDto() {
    var sortBy: ComparisonSortBy = ComparisonSortBy.PRICE

    var sortOrder: SortOrder = SortOrder.ASC
}
