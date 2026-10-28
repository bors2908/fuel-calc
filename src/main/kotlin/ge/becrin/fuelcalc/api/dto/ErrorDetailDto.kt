package ge.becrin.fuelcalc.api.dto


data class ErrorDetailDto(
    val field: String?,

    val code: String,

    val message: String
)
