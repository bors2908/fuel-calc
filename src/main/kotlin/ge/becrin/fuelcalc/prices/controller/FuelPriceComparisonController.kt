package ge.becrin.fuelcalc.prices.controller

import ge.becrin.fuelcalc.prices.dto.FuelPriceComparisonQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceComparisonDto
import ge.becrin.fuelcalc.prices.service.FuelPriceComparisonService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FuelPriceComparisonController(private val service: FuelPriceComparisonService) {
    @GetMapping("/api/v1/fuel-prices/comparison")
    fun process(@Valid query: FuelPriceComparisonQueryDto): PriceComparisonDto =
        service.get(query)
}
