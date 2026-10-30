package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.PriceChangeStatus
import ge.becrin.fuelcalc.prices.dto.PriceChangeDto
import ge.becrin.fuelcalc.prices.dto.PriceOverviewDto
import ge.becrin.fuelcalc.prices.model.ComparisonAnalysis
import ge.becrin.fuelcalc.prices.model.PriceReadContext
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.SourcePriceRows
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Service

@Service
class FuelPriceOverviewService(
    private val cells: PriceCellConverter,

    private val comparisons: ComparisonConverter
) {
    fun build(
        context: PriceReadContext,
        rows: SourcePriceRows,
        analysis: ComparisonAnalysis,
        last: PriceRow? = rows.byRegion[context.selection.base.okatoCode]?.lastOrNull()
    ): PriceOverviewDto {
        val base = rows.byRegion[context.selection.base.okatoCode].orEmpty()
        val first = base.firstOrNull()
        val firstCell = first?.let { cells.available(it) } ?: cells.missing()
        val lastCell = last?.let { cells.available(it) } ?: cells.missing()
        val enough = first != null && last != null && first.period.key != last.period.key
        val difference = if (enough) checkNotNull(last).price.subtract(checkNotNull(first).price) else null
        val status =
            when {
                !enough -> PriceChangeStatus.INSUFFICIENT_DATA
                checkNotNull(first).price.signum() == 0 -> PriceChangeStatus.ZERO_BASE
                else -> PriceChangeStatus.AVAILABLE
            }
        val percent =
            if (status == PriceChangeStatus.AVAILABLE) {
                checkNotNull(difference).multiply(BigDecimal("100")).divide(checkNotNull(first).price, 2, RoundingMode.HALF_UP)
                    .toPlainString()
            } else {
                null
            }
        val change = PriceChangeDto(status, firstCell, difference?.toPlainString(), percent)

        return PriceOverviewDto(
            latestPrice = lastCell,
            latestAvailableDate = context.bounds[context.selection.base.okatoCode]?.last,
            change = change,
            comparison = comparisons.summary(analysis)
        )
    }
}
