package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonProperty
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import java.time.LocalDate

data class ComparisonRowDto(
    val region: RegionRefDto,

    @get:JsonProperty("isBase")
    val isBase: Boolean,

    val price: PriceCellDto,

    val latestAvailableDate: LocalDate?,

    val differenceRubPerLiter: String?,

    @get:JsonProperty("isLowest")
    val isLowest: Boolean
)
