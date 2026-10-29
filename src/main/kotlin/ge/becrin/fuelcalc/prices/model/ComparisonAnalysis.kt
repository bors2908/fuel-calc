package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.api.SortOrder
import ge.becrin.fuelcalc.prices.ComparisonSortBy
import ge.becrin.fuelcalc.regions.model.Region
import java.math.BigDecimal

data class ComparisonAnalysis(
    val snapshot: ComparisonSnapshot,

    val sortBy: ComparisonSortBy,

    val sortOrder: SortOrder,

    val rows: List<ComparisonRow>,

    val allPricesEqual: Boolean?,

    val minimum: BigDecimal?,

    val lowestRegions: List<Region>
)
