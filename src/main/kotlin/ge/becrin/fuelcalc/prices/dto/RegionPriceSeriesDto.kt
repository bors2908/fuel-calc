package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.PriceSeriesStatus
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import java.time.LocalDate

data class RegionPriceSeriesDto(
    val region: RegionRefDto,

    val status: PriceSeriesStatus,

    val firstAvailableDate: LocalDate?,

    val lastAvailableDate: LocalDate?,

    val points: List<PriceCellDto>
)
