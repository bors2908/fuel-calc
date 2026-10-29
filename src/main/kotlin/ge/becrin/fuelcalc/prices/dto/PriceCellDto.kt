package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.sources.PriceSource

sealed interface PriceCellDto {
    val priceRubPerLiter: String?

    val observationId: String?

    val period: PricePeriodDto?

    val source: PriceSource?

    val missingReason: MissingPriceReason?
}
