package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.PriceFrequency

data class PriceSourcePageDto(
    val frequency: PriceFrequency,

    val overview: PriceOverviewDto,

    val trend: List<RegionPriceSeriesDto>,

    val comparison: ComparisonDto<ComparisonRowDto>
)
