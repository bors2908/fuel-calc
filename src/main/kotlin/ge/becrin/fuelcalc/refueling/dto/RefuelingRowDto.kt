package ge.becrin.fuelcalc.refueling.dto

import ge.becrin.fuelcalc.prices.dto.AvailablePriceCellDto
import ge.becrin.fuelcalc.refueling.RefuelingRelation
import ge.becrin.fuelcalc.regions.dto.RegionRefDto

data class RefuelingRowDto(
    val region: RegionRefDto,

    val price: AvailablePriceCellDto,

    val totalRub: String,

    val differenceRub: String,

    val relation: RefuelingRelation
)
