package ge.becrin.fuelcalc.refueling.dto

data class RefuelingCalculateRequestDto(
    val selection: RefuelingSelectionDto,

    val volumeLiters: String
)
