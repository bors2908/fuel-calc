package ge.becrin.fuelcalc.refueling.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.prices.dto.ComparisonDto
import ge.becrin.fuelcalc.prices.dto.PriceContextDto

data class RefuelingCalculationDto(
    val meta: ApiMetaDto,

    val context: PriceContextDto,

    val volumeLiters: String,

    val currency: String,

    val baseTotalRub: String,

    val expectedSnapshotId: String?,

    val snapshotChanged: Boolean?,

    val comparison: ComparisonDto,

    val data: List<RefuelingRowDto>
)
