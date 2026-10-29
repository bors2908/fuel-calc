package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.SortOrder
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.ComparisonSortBy
import ge.becrin.fuelcalc.sources.PriceSource
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import org.springframework.format.annotation.DateTimeFormat

class PricePageQueryDto {
    var sortSource: PriceSource = PriceSource.OFFICIAL

    var sortBy: ComparisonSortBy = ComparisonSortBy.PRICE

    var sortOrder: SortOrder = SortOrder.ASC

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
}
