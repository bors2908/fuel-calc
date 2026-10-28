package ge.becrin.fuelcalc.prices.controller

import ge.becrin.fuelcalc.prices.dto.FuelPriceQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceTrendDto
import ge.becrin.fuelcalc.prices.service.FuelPriceTrendService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FuelPriceTrendController(private val service: FuelPriceTrendService) {
    @GetMapping("/api/v1/fuel-prices/trend")
    fun process(@Valid query: FuelPriceQueryDto): PriceTrendDto =
        service.get(query)
}
