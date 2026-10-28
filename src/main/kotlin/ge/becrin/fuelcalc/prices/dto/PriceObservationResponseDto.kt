package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto

data class PriceObservationResponseDto(
    val meta: ApiMetaDto,

    val observation: PriceObservationDto,

    val sourceUpdates: Map<String, SourceUpdateDto>
)
