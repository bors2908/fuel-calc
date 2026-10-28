package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto
import java.time.LocalDate

data class PriceOverviewDto(
    val meta: ApiMetaDto,

    val context: PriceContextDto,

    val latestPrice: PriceCellDto,

    val latestAvailableDate: LocalDate?,

    val change: PriceChangeDto,

    val comparison: ComparisonSummaryDto,

    val sourceUpdates: Map<String, SourceUpdateDto>
)
