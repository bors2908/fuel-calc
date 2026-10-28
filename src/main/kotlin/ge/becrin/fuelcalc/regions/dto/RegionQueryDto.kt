package ge.becrin.fuelcalc.regions.dto

import jakarta.validation.constraints.Size

class RegionQueryDto {
    @field:Size(max = 100)
    var filterName: String? = null
}
