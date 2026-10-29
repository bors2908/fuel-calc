package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.api.SortOrder
import ge.becrin.fuelcalc.prices.ComparisonSortBy
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.dto.ComparisonDto
import ge.becrin.fuelcalc.prices.dto.ComparisonRowDto
import ge.becrin.fuelcalc.prices.dto.ComparisonSummaryDto
import ge.becrin.fuelcalc.prices.model.ComparisonSnapshot
import ge.becrin.fuelcalc.prices.model.ComparisonRow
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import org.springframework.stereotype.Component

@Component
class ComparisonConverter(private val cells: PriceCellConverter) {
    fun toDto(
        snapshot: ComparisonSnapshot,
        context: PriceReadContextFactory.ReadContext,
        sortBy: ComparisonSortBy = ComparisonSortBy.PRICE,
        sortOrder: SortOrder = SortOrder.ASC
    ): ComparisonDto<ComparisonRowDto> =
        convert(snapshot, sortBy, sortOrder) { row ->
            ComparisonRowDto(
                region = row.region,
                isBase = row.isBase,
                price = row.price?.let(cells::available) ?: cells.missing(),
                latestAvailableDate = context.bounds[row.region.okatoCode]?.last,
                differenceRubPerLiter = row.difference?.toPlainString(),
                isLowest = row.isLowest,
                isHighest = row.isHighest
            )
        }

    fun <T> convert(
        snapshot: ComparisonSnapshot,
        sortBy: ComparisonSortBy = ComparisonSortBy.PRICE,
        sortOrder: SortOrder = SortOrder.ASC,
        rowConverter: (ComparisonRow) -> T
    ): ComparisonDto<T> {
        val comparable = snapshot.status == ComparisonStatus.AVAILABLE
        val unavailable = snapshot.status == ComparisonStatus.NO_DATA || snapshot.status == ComparisonStatus.NO_COMMON_PERIOD
        val actualSort = if (unavailable && sortBy == ComparisonSortBy.PRICE) ComparisonSortBy.REGION_NAME else sortBy
        val actualOrder = if (unavailable && sortBy == ComparisonSortBy.PRICE) SortOrder.ASC else sortOrder
        val rows = snapshot.rows.associateBy { it.region.okatoCode }
        val minimum = snapshot.rows.minOfOrNull { it.price }
        val maximum = snapshot.rows.maxOfOrNull { it.price }
        val equal = comparable && minimum?.compareTo(maximum) == 0
        val base = rows[snapshot.selection.base.okatoCode]
        val primary =
            if (actualSort == ComparisonSortBy.PRICE) compareBy<RegionRefDto> { rows.getValue(it.okatoCode).price }
            else compareBy { it.name }
        val directed = if (actualOrder == SortOrder.DESC) primary.reversed() else primary
        val sorted = snapshot.selection.regions.sortedWith(directed.thenBy { it.name }.thenBy { it.okatoCode })
        val data = sorted.map { region ->
            val row = rows[region.okatoCode]
            val isBase = region.okatoCode == snapshot.selection.base.okatoCode
            rowConverter(
                ComparisonRow(
                    region = region,
                    isBase = isBase,
                    price = row,
                    difference = if (comparable && !isBase) checkNotNull(row).price.subtract(checkNotNull(base).price) else null,
                    isLowest = comparable && !equal && row?.price?.compareTo(minimum) == 0,
                    isHighest = comparable && !equal && row?.price?.compareTo(maximum) == 0
                )
            )
        }

        return ComparisonDto(
            snapshot.status,
            snapshot.snapshotId,
            snapshot.period,
            actualSort,
            actualOrder,
            data,
            if (comparable) equal else null,
            snapshot.hasNewerObservations
        )
    }

    fun summary(snapshot: ComparisonSnapshot): ComparisonSummaryDto {
        val available = snapshot.status == ComparisonStatus.AVAILABLE
        val base = snapshot.rows.singleOrNull { it.region.okatoCode == snapshot.selection.base.okatoCode }
        val minimum = if (available) snapshot.rows.minOf { it.price } else null
        val lowest =
            if (available) snapshot.rows.filter { it.price.compareTo(minimum) == 0 }.map { it.region }.sortedBy { it.okatoCode }
            else emptyList()

        return ComparisonSummaryDto(
            snapshot.status,
            snapshot.snapshotId,
            snapshot.period,
            if (available || snapshot.status == ComparisonStatus.SINGLE_REGION) base?.price?.toPlainString() else null,
            minimum?.toPlainString(),
            lowest,
            minimum?.subtract(checkNotNull(base).price)?.toPlainString(),
            snapshot.hasNewerObservations
        )
    }
}
