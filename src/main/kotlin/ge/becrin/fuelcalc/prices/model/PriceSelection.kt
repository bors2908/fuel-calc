package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.regions.model.Region
import java.time.LocalDate

data class PriceSelection(
    val base: Region,

    val regions: List<Region>,

    val fuel: FuelType,

    val from: LocalDate,

    val until: LocalDate
)
