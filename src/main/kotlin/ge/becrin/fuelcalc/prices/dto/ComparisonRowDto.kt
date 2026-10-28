package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonProperty
import ge.becrin.fuelcalc.regions.dto.RegionRefDto

data class ComparisonRowDto(
    val region: RegionRefDto,

    @get:JsonProperty("isBase")
    val isBase: Boolean,

    val price: PriceCellDto,

    val differenceRubPerLiter: String?,

    @get:JsonProperty("isLowest")
    val isLowest: Boolean,

    @get:JsonProperty("isHighest")
    val isHighest: Boolean
)
