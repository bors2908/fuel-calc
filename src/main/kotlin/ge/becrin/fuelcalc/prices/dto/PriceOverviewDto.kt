package ge.becrin.fuelcalc.prices.dto

import java.time.LocalDate

data class PriceOverviewDto(
    val latestPrice: PriceCellDto,

    val latestAvailableDate: LocalDate?,

    val change: PriceChangeDto,

    val comparison: ComparisonSummaryDto
)
