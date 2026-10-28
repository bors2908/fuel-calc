package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto

data class PriceComparisonDto(
    val meta: ApiMetaDto,

    val context: PriceContextDto,

    val comparison: ComparisonDto<ComparisonRowDto>,

    val sourceUpdates: Map<String, SourceUpdateDto>
)
