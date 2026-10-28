package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.sources.PriceSource
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class CoverageQueryDto {
    @field:NotBlank
    var filterRegionCode: String? = null

    var filterFuelType: String? = null

    @field:NotNull
    var preferredSource: PriceSource? = null

    @field:Size(min = 1, max = 128)
    var dataVersion: String? = null
}
