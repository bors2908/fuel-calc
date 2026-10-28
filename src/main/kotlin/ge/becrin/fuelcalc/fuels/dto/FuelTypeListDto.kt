package ge.becrin.fuelcalc.fuels.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto

data class FuelTypeListDto(
    val meta: ApiMetaDto,

    val data: List<FuelTypeRefDto>
)
