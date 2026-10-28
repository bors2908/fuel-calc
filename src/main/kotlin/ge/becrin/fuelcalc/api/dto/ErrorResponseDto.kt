package ge.becrin.fuelcalc.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

data class ErrorResponseDto(
    val type: String,

    val message: String,

    val timestamp: Instant,

    val requestId: String,

    val details: List<ErrorDetailDto>,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val currentDataVersion: String? = null
)
