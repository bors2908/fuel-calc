package ge.becrin.fuelcalc.sources.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.prices.dto.RegionalPriceSampleDto
import ge.becrin.fuelcalc.sources.PriceAggregationMethod

data class SourceSummaryDto(
    val publisher: SourceRefDto,

    val provider: SourceRefDto,

    val datasetId: String,

    val publicationId: String?,

    val aggregationMethod: PriceAggregationMethod,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val sample: RegionalPriceSampleDto? = null
)
