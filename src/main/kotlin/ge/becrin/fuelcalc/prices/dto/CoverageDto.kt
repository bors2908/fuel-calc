package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate

data class CoverageDto(
    val region: RegionRefDto,

    val fuelType: FuelTypeRefDto,

    val frequency: PriceFrequency,

    val firstAvailableDate: LocalDate?,

    val lastAvailableDate: LocalDate?,

    val observationCount: Int,

    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val latestSource: PriceSource? = null
)
