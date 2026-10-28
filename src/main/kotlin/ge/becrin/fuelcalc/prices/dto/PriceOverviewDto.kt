package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto

data class PriceOverviewDto(
    val meta: ApiMetaDto,

    val context: PriceContextDto,

    val latestPrice: PriceCellDto,

    val change: PriceChangeDto,

    val comparison: ComparisonSummaryDto
)
