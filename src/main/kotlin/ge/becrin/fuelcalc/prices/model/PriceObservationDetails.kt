package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.sources.dto.SourceRefDto
import java.time.Instant
import java.time.LocalDate

data class PriceObservationDetails(
    val price: PriceRow,

    val publisher: SourceRefDto,

    val providerUrl: String?,

    val datasetId: String,

    val publicationId: String?,

    val publicationDate: LocalDate?,

    val publishedAt: Instant?,

    val loadedAt: Instant,

    val publicationUrl: String?,

    val attribution: String,

    val methodologyUrl: String?,

    val note: String?
)
