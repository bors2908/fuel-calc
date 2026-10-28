package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto

data class PriceComparisonDto(
    val meta: ApiMetaDto,

    val context: PriceContextDto,

    val comparison: ComparisonDto
)
