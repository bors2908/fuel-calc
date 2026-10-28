package ge.becrin.fuelcalc.refueling.controller

import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculateRequestDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculationDto
import ge.becrin.fuelcalc.refueling.service.RefuelingCalculationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RefuelingController(private val service: RefuelingCalculationService) {
    @PostMapping("/api/v1/refueling/calculate", consumes = ["application/json"])
    fun process(@Valid @RequestBody request: RefuelingCalculateRequestDto): RefuelingCalculationDto =
        service.calculate(request)
}
