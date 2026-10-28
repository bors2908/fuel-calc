package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto

data class PriceObservationResponseDto(
    val meta: ApiMetaDto,

    val observation: PriceObservationDto
)
