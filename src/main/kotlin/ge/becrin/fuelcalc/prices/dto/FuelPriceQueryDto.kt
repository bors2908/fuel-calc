package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceSource
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

open class FuelPriceQueryDto {
    @field:NotBlank
    var baseRegionCode: String? = null

    @field:NotBlank
    var filterRegionCode: String? = null

    @field:NotNull
    var filterFuelType: FuelType? = null

    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var filterDateFrom: LocalDate? = null

    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var filterDateTo: LocalDate? = null

    @field:NotNull
    var frequency: PriceFrequency? = null

    @field:NotNull
    var preferredSource: PriceSource? = null

    @field:Size(min = 1, max = 128)
    var dataVersion: String? = null
}
