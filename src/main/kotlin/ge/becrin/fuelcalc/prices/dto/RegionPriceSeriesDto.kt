package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.PriceSeriesStatus
import ge.becrin.fuelcalc.regions.dto.RegionRefDto

data class RegionPriceSeriesDto(
    val region: RegionRefDto,

    val status: PriceSeriesStatus,

    val points: List<TrendPointDto>
)
