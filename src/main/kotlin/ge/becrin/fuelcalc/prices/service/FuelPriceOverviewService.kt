package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.PriceChangeStatus
import ge.becrin.fuelcalc.prices.dto.FuelPriceQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceChangeDto
import ge.becrin.fuelcalc.prices.dto.PriceOverviewDto
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class FuelPriceOverviewService(
    private val archive: ArchiveReadService,

    private val contexts: PriceReadContextFactory,

    private val query: PriceQueryExecutor,

    private val cells: PriceCellConverter,

    private val resolver: ComparisonResolver,

    private val comparisons: ComparisonConverter
) {
    fun get(input: FuelPriceQueryDto): PriceOverviewDto =
        archive.read(input.dataVersion) { meta ->
            val context = contexts.query(input, meta.serverDate)
            val boundaries = query.boundaries(context.selection)
            val base = boundaries.filter { it.region.okatoCode == context.selection.base.okatoCode }.sortedBy { it.period.referenceDate }
            val first = base.firstOrNull()
            val last = base.lastOrNull()
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

            PriceOverviewDto(
                meta = meta,
                context = context.dto,
                latestPrice = lastCell,
                latestAvailableDate = context.bounds[context.selection.base.okatoCode]?.last,
                change = change,
                comparison = comparisons.summary(resolver.resolve(context.selection, boundaries)),
                sourceUpdates = context.updates
            )
        }
}
