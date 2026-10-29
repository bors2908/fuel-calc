package ge.becrin.fuelcalc.refueling.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto

data class RefuelingCalculationDto(
    val meta: ApiMetaDto,

    val volumeLiters: String,

    val currency: String,

    val results: List<RefuelingSourceCalculationDto>,

    val sourceUpdates: Map<String, SourceUpdateDto>
)
