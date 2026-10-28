package ge.becrin.fuelcalc.refueling.dto

import com.fasterxml.jackson.annotation.JsonProperty
import ge.becrin.fuelcalc.prices.dto.AvailablePriceCellDto
import ge.becrin.fuelcalc.refueling.RefuelingRelation
import ge.becrin.fuelcalc.regions.dto.RegionRefDto

data class RefuelingRowDto(
    val region: RegionRefDto,

    @get:JsonProperty("isBase")
    val isBase: Boolean,

    val price: AvailablePriceCellDto,

    val totalRub: String,

    val differenceRub: String,

    val relation: RefuelingRelation
)
