package ge.becrin.fuelcalc.api.error

import ge.becrin.fuelcalc.api.dto.ErrorDetailDto

class ApiException(
    val status: Int,

    val type: String,

    message: String,

    val details: List<ErrorDetailDto>,

    val currentDataVersion: String? = null
) : RuntimeException(message) {
    companion object {
        fun invalid(
            field: String?,
            code: String,
            message: String
        ): ApiException =
            ApiException(400, "InvalidInput", message, listOf(ErrorDetailDto(field, code, message)))
    }
}
