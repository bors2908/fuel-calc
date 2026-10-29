package ge.becrin.fuelcalc.fuels.dto

import ge.becrin.fuelcalc.fuels.FuelType

data class FuelTypeRefDto(
    val code: FuelType,

    val name: String = code.displayName
)
