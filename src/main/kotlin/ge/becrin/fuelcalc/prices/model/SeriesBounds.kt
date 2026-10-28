package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate

data class SeriesBounds(
    val regionCode: String,

    val fuel: FuelType,

    val frequency: PriceFrequency,

    val first: LocalDate,

    val last: LocalDate,

    val count: Int,

    val latestProvider: PriceSource
)
