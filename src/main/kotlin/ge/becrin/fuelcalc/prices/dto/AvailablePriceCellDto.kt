package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.sources.PriceSource

data class AvailablePriceCellDto(
    override val priceRubPerLiter: String,

    override val observationId: String,

    override val period: PricePeriodDto,

    override val source: PriceSource
) : PriceCellDto {
    override val missingReason: MissingPriceReason? = null
}
