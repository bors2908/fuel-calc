package ge.becrin.fuelcalc.api.dto

import java.time.Instant

data class ErrorResponseDto(
    val type: String,

    val message: String,

    val timestamp: Instant,

    val requestId: String,

    val details: List<ErrorDetailDto>
)
