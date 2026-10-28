package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import java.time.Instant
import java.time.LocalDate

data class PriceObservationDto(
    val observationId: String,

    val region: RegionRefDto,

    val fuelType: FuelTypeRefDto,

    val basis: PriceBasis,

    val unit: String,

    val price: AvailablePriceCellDto,

    val publicationDate: LocalDate?,

    val publishedAt: Instant?,

    val loadedAt: Instant,

    val publicationUrl: String?,

    val attribution: String,

    val methodologyUrl: String?,

    val note: String?
)
