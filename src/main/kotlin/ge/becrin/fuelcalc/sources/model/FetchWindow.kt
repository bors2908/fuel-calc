package ge.becrin.fuelcalc.sources.model

import java.time.LocalDate

data class FetchWindow(
    val from: LocalDate,

    val until: LocalDate
)
