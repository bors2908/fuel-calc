package ge.becrin.fuelcalc.regions.controller

import ge.becrin.fuelcalc.regions.dto.RegionListDto
import ge.becrin.fuelcalc.regions.dto.RegionQueryDto
import ge.becrin.fuelcalc.regions.service.RegionQueryService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RegionController(private val service: RegionQueryService) {
    @GetMapping("/api/v1/regions")
    fun process(@Valid query: RegionQueryDto): RegionListDto =
        service.list(query)
}
