package ge.becrin.fuelcalc.prices.controller

import ge.becrin.fuelcalc.prices.dto.FuelPriceQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceOverviewDto
import ge.becrin.fuelcalc.prices.service.FuelPriceOverviewService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FuelPriceOverviewController(private val service: FuelPriceOverviewService) {
    @GetMapping("/api/v1/fuel-prices/overview")
    fun process(@Valid query: FuelPriceQueryDto): PriceOverviewDto =
        service.get(query)
}
