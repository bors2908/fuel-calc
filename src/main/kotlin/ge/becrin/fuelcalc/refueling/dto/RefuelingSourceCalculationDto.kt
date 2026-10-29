package ge.becrin.fuelcalc.refueling.dto

import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.dto.ComparisonDto
import ge.becrin.fuelcalc.prices.dto.PriceContextDto

data class RefuelingSourceCalculationDto(
    val context: PriceContextDto,

    val status: ComparisonStatus,

    val baseTotalRub: String?,

    val snapshotChanged: Boolean?,

    val comparison: ComparisonDto<RefuelingRowDto>?
)
