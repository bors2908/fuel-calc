package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.MissingPriceReason
import java.time.LocalDate

data class TrendPointDto(
    val date: LocalDate,

    val priceRubPerLiter: String?,

    val observationId: String?,

    val missingReason: MissingPriceReason?
)
