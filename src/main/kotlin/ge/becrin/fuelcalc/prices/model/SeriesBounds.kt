package ge.becrin.fuelcalc.prices.model

import java.time.LocalDate

data class SeriesBounds(
    val regionCode: String,

    val first: LocalDate,

    val last: LocalDate
)
