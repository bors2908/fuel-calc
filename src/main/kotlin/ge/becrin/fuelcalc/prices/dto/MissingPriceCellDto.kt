package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto

data class MissingPriceCellDto(
    override val priceRubPerLiter: String?,

    override val observationId: String?,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    override val period: PricePeriodDto? = null,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    override val source: SourceSummaryDto? = null,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    override val freshness: PriceFreshnessDto? = null,

    override val missingReason: String
) : PriceCellDto
