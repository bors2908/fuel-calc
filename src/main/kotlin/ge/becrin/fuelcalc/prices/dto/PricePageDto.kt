package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto

data class PricePageDto(
    val meta: ApiMetaDto,

    val context: PricePageContextDto,

    val sources: Map<PriceSource, PriceSourcePageDto>,

    val comparisonOrder: PriceSource,

    val sourceUpdates: Map<String, SourceUpdateDto>
)
