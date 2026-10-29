package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.sources.SourceProvider
import java.time.Instant
import java.time.LocalDate

data class PriceObservationDetails(
    val price: PriceRow,

    val provider: SourceProvider,

    val publicationDate: LocalDate?,

    val loadedAt: Instant
)
