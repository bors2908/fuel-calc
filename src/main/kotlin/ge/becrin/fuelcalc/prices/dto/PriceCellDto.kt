package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto

sealed interface PriceCellDto {
    val priceRubPerLiter: String?

    val observationId: String?

    val period: PricePeriodDto?

    val source: SourceSummaryDto?

    val missingReason: MissingPriceReason?
}
