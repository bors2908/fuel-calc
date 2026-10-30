package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.MissingPriceReason
import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.prices.forecast.ForecastBasis
import java.time.LocalDate

data class TrendPointDto(
    val date: LocalDate,

    val priceRubPerLiter: String?,

    val observationId: String?,

    val missingReason: MissingPriceReason?,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val forecast: ForecastBasis? = null,

    val kind: String = "OBSERVED"
)
