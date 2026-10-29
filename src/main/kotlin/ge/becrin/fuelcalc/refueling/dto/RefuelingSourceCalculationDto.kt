package ge.becrin.fuelcalc.refueling.dto

import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.dto.ComparisonDto
import ge.becrin.fuelcalc.sources.PriceSource

data class RefuelingSourceCalculationDto(
    val source: PriceSource,

    val frequency: PriceFrequency,

    val status: ComparisonStatus,

    val baseTotalRub: String?,

    val comparison: ComparisonDto<RefuelingRowDto>?
)
