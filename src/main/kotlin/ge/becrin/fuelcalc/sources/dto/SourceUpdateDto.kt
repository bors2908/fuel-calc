package ge.becrin.fuelcalc.sources.dto

import ge.becrin.fuelcalc.sources.SourceUpdateStatus
import java.time.Instant

data class SourceUpdateDto(
    val status: SourceUpdateStatus,

    val lastSuccessAt: Instant?,

    val nextExpectedPublicationAt: Instant?,

    val message: String?
)
