package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto

data class AvailablePriceCellDto(
    override val priceRubPerLiter: String,

    override val observationId: String,

    override val period: PricePeriodDto,

    override val source: SourceSummaryDto,

    override val freshness: PriceFreshnessDto,

    override val missingReason: String?
) : PriceCellDto
