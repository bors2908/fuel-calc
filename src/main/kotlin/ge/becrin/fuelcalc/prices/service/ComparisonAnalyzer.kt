package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.api.SortOrder
import ge.becrin.fuelcalc.prices.ComparisonSortBy
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.model.ComparisonAnalysis
import ge.becrin.fuelcalc.prices.model.ComparisonRow
import ge.becrin.fuelcalc.prices.model.ComparisonSnapshot
import ge.becrin.fuelcalc.regions.model.Region
import org.springframework.stereotype.Component

@Component
class ComparisonAnalyzer {
    fun analyze(
        snapshot: ComparisonSnapshot,
        sortBy: ComparisonSortBy = ComparisonSortBy.PRICE,
        sortOrder: SortOrder = SortOrder.ASC
    ): ComparisonAnalysis {
        val comparable = snapshot.status == ComparisonStatus.AVAILABLE
        val unavailable = snapshot.status == ComparisonStatus.NO_DATA || snapshot.status == ComparisonStatus.NO_COMMON_PERIOD
        val actualSort = if (unavailable && sortBy == ComparisonSortBy.PRICE) ComparisonSortBy.REGION_NAME else sortBy
        val actualOrder = if (unavailable && sortBy == ComparisonSortBy.PRICE) SortOrder.ASC else sortOrder
        val prices = snapshot.rows.associateBy { it.region.okatoCode }
        val minimum = if (comparable) snapshot.rows.minOf { it.price } else null
        val equal = comparable && minimum?.compareTo(snapshot.rows.maxOf { it.price }) == 0
        val base = prices[snapshot.selection.base.okatoCode]
        val primary =
            if (actualSort == ComparisonSortBy.PRICE) {
                compareBy<Region> { prices.getValue(it.okatoCode).price }
            } else {
                compareBy { it.name }
            }
        val directed = if (actualOrder == SortOrder.DESC) primary.reversed() else primary
        val sorted = snapshot.selection.regions.sortedWith(directed.thenBy { it.name }.thenBy { it.okatoCode })
        val rows = sorted.map { region ->
            val price = prices[region.okatoCode]
            val isBase = region.okatoCode == snapshot.selection.base.okatoCode

            ComparisonRow(
                region = region,
                isBase = isBase,
                price = price,
                difference = if (comparable && !isBase) checkNotNull(price).price.subtract(checkNotNull(base).price) else null,
                isLowest = comparable && !equal && price?.price?.compareTo(minimum) == 0
            )
        }
        val lowest =
            if (comparable) snapshot.rows.filter { it.price.compareTo(minimum) == 0 }.map { it.region }.sortedBy { it.okatoCode }
            else emptyList()

        return ComparisonAnalysis(snapshot, actualSort, actualOrder, rows, if (comparable) equal else null, minimum, lowest)
    }
}
