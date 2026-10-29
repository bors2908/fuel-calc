package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import java.time.LocalDate

data class PricePageContextDto(
    val baseRegion: RegionRefDto,

    val regions: List<RegionRefDto>,

    val fuelType: FuelTypeRefDto,

    val dateFrom: LocalDate,

    val dateTo: LocalDate
)
