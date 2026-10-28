package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.PriceFrequency
import java.time.LocalDate

data class PricePeriodDto(
    val key: String,

    val frequency: PriceFrequency,

    val referenceDate: LocalDate,

    val label: String,

    val startDate: LocalDate?,

    val endDate: LocalDate?
)
