package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.dto.SourceRefDto
import java.time.Instant
import java.time.LocalDate

data class PriceObservationDto(
    val region: RegionRefDto,

    val fuelType: FuelTypeRefDto,

    val basis: PriceBasis,

    val unit: String,

    val price: AvailablePriceCellDto,

    val latestAvailableDate: LocalDate?,

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
