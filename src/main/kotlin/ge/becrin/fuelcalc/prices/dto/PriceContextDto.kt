package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate

data class PriceContextDto(
    val key: String,

    val baseRegion: RegionRefDto,

    val regions: List<RegionRefDto>,

    val fuelType: FuelTypeRefDto,

    val dateFrom: LocalDate,

    val dateTo: LocalDate,

    val frequency: PriceFrequency,

    val source: PriceSource,

    val basis: PriceBasis,

    val unit: String
)
