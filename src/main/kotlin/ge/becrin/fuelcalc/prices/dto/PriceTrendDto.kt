package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto

data class PriceTrendDto(
    val meta: ApiMetaDto,

    val context: PriceContextDto,

    val data: List<RegionPriceSeriesDto>,

    val sourceUpdates: Map<String, SourceUpdateDto>
)
