package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.sources.dto.SourceRefDto
import java.time.Instant
import java.time.LocalDate

data class PriceObservationDetails(
    val price: PriceRow,

    val provider: SourceRefDto,

    val publicationDate: LocalDate?,

    val loadedAt: Instant
)
