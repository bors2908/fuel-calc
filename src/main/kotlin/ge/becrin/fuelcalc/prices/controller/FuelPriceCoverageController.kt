package ge.becrin.fuelcalc.prices.controller

import ge.becrin.fuelcalc.prices.dto.CoverageListDto
import ge.becrin.fuelcalc.prices.dto.CoverageQueryDto
import ge.becrin.fuelcalc.prices.service.FuelPriceCoverageService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FuelPriceCoverageController(private val service: FuelPriceCoverageService) {
    @GetMapping("/api/v1/fuel-prices/coverage")
    fun process(@Valid query: CoverageQueryDto): CoverageListDto =
        service.get(query)
}
