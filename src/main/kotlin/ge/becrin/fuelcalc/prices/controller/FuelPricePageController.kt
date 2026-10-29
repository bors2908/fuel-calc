package ge.becrin.fuelcalc.prices.controller

import ge.becrin.fuelcalc.prices.dto.PricePageDto
import ge.becrin.fuelcalc.prices.dto.PricePageQueryDto
import ge.becrin.fuelcalc.prices.service.PricePageService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FuelPricePageController(private val service: PricePageService) {
    @GetMapping("/api/v1/fuel-prices")
    fun process(@Valid query: PricePageQueryDto): PricePageDto =
        service.get(query)
}
