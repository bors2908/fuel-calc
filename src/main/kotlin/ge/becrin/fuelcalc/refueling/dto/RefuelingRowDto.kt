package ge.becrin.fuelcalc.refueling.dto

import com.fasterxml.jackson.annotation.JsonProperty
import ge.becrin.fuelcalc.prices.dto.AvailablePriceCellDto
import ge.becrin.fuelcalc.refueling.RefuelingRelation
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import java.time.LocalDate

data class RefuelingRowDto(
    val region: RegionRefDto,

    val price: AvailablePriceCellDto,

    val latestAvailableDate: LocalDate?,

    val differenceRubPerLiter: String?,

    @get:JsonProperty("isLowest")
    val isLowest: Boolean,

    @get:JsonProperty("isHighest")
    val isHighest: Boolean,

    val totalRub: String,

    val differenceRub: String,

    val relation: RefuelingRelation
)
