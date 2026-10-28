package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto

data class MissingPriceCellDto(
    override val missingReason: MissingPriceReason,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    override val period: PricePeriodDto? = null
) : PriceCellDto {
    override val priceRubPerLiter: String? = null

    override val observationId: String? = null

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    override val source: SourceSummaryDto? = null
}
