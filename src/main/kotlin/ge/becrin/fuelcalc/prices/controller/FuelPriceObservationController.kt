package ge.becrin.fuelcalc.prices.controller

import ge.becrin.fuelcalc.prices.dto.PriceObservationResponseDto
import ge.becrin.fuelcalc.prices.service.FuelPriceObservationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class FuelPriceObservationController(private val service: FuelPriceObservationService) {
    @GetMapping("/api/v1/fuel-prices/observations/{observationId}")
    fun process(@PathVariable observationId: String): PriceObservationResponseDto =
        service.get(observationId)
}
