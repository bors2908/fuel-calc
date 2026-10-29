package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.dto.ComparisonDto
import ge.becrin.fuelcalc.prices.dto.ComparisonRowDto
import ge.becrin.fuelcalc.prices.dto.ComparisonSummaryDto
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.prices.model.ComparisonAnalysis
import ge.becrin.fuelcalc.prices.model.ComparisonRow
import ge.becrin.fuelcalc.prices.model.PriceReadContext
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import org.springframework.stereotype.Component

@Component
class ComparisonConverter(private val cells: PriceCellConverter) {
    fun toDto(
        analysis: ComparisonAnalysis,
        context: PriceReadContext
    ): ComparisonDto<ComparisonRowDto> =
        convert(analysis) { row ->
            ComparisonRowDto(
                region = RegionRefDto(row.region),
                isBase = row.isBase,
                price = row.price?.let(cells::available) ?: cells.missing(),
                latestAvailableDate = context.bounds[row.region.okatoCode]?.last,
                differenceRubPerLiter = row.difference?.toPlainString(),
                isLowest = row.isLowest
            )
        }

    fun <T> convert(
        analysis: ComparisonAnalysis,
        rowConverter: (ComparisonRow) -> T
    ): ComparisonDto<T> =
        ComparisonDto(
            analysis.snapshot.status,
            analysis.snapshot.period?.let(::PricePeriodDto),
            analysis.sortBy,
            analysis.sortOrder,
            analysis.rows.map(rowConverter),
            analysis.allPricesEqual,
            analysis.snapshot.hasNewerObservations
        )

    fun summary(analysis: ComparisonAnalysis): ComparisonSummaryDto =
        ComparisonSummaryDto(
            analysis.snapshot.status,
            analysis.snapshot.period?.let(::PricePeriodDto),
            analysis.minimum?.toPlainString(),
            analysis.lowestRegions.map(::RegionRefDto),
            analysis.snapshot.hasNewerObservations
        )
}
