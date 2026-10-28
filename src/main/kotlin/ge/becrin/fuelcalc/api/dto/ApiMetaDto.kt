package ge.becrin.fuelcalc.api.dto

import java.time.Instant
import java.time.LocalDate

data class ApiMetaDto(
    val requestId: String,

    val generatedAt: Instant,

    val serverDate: LocalDate,

    val dataVersion: String
)
