package ge.becrin.fuelcalc.regions.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto

data class RegionListDto(
    val meta: ApiMetaDto,

    val data: List<RegionRefDto>
)
