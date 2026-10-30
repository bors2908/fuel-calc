package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.MissingPriceReason
import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.prices.forecast.ForecastBasis
import ge.becrin.fuelcalc.sources.PriceSource

data class AvailablePriceCellDto(
    override val priceRubPerLiter: String,

    override val observationId: String?,

    override val period: PricePeriodDto,

    override val source: PriceSource,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val forecast: ForecastBasis? = null
) : PriceCellDto {
    override val missingReason: MissingPriceReason? = null
}
