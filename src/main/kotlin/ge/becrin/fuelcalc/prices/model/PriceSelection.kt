package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate

data class PriceSelection(
    val base: RegionRefDto,

    val regions: List<RegionRefDto>,

    val fuel: FuelTypeRefDto,

    val from: LocalDate,

    val until: LocalDate,

    val frequency: PriceFrequency,

    val source: PriceSource
)
