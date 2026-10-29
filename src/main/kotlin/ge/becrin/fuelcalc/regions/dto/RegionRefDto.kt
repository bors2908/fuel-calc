package ge.becrin.fuelcalc.regions.dto

import ge.becrin.fuelcalc.regions.model.Region

data class RegionRefDto(
    val okatoCode: String,

    val name: String
) {
    constructor(region: Region) : this(region.okatoCode, region.name)
}
