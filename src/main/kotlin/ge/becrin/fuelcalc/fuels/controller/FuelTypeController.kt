package ge.becrin.fuelcalc.fuels.controller

import ge.becrin.fuelcalc.fuels.dto.FuelTypeListDto
import ge.becrin.fuelcalc.fuels.service.FuelTypeQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FuelTypeController(private val service: FuelTypeQueryService) {
    @GetMapping("/api/v1/fuel-types")
    fun process(): FuelTypeListDto =
        service.list()
}
