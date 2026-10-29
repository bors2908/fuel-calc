package ge.becrin.fuelcalc.sources.dto

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceUpdateStatus
import java.time.Instant

data class SourceUpdateDto(
    val source: PriceSource,

    val frequency: PriceFrequency,

    val status: SourceUpdateStatus,

    val lastSuccessAt: Instant?
)
