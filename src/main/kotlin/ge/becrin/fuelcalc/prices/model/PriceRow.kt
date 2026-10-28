package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class PriceRow(
    val id: Long,

    val region: RegionRefDto,

    val fuel: FuelTypeRefDto,

    val period: PricePeriodDto,

    val price: BigDecimal,

    val feedId: Long,

    val source: SourceSummaryDto,

    val publicationDate: LocalDate?,

    val publishedAt: Instant?,

    val loadedAt: Instant,

    val publicationUrl: String?,

    val attribution: String,

    val methodologyUrl: String?,

    val note: String?
)
