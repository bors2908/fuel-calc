package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto

data class PriceTrendDto(
    val meta: ApiMetaDto,

    val context: PriceContextDto,

    val data: List<RegionPriceSeriesDto>
)
