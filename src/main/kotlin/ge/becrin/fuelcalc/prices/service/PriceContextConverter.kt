package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.dto.PricePageContextDto
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import org.springframework.stereotype.Component

@Component
class PriceContextConverter {
    fun page(selection: PriceSelection): PricePageContextDto =
        PricePageContextDto(
            baseRegion = RegionRefDto(selection.base),
            regions = selection.regions.map(::RegionRefDto),
            fuelType = FuelTypeRefDto(selection.fuel),
            dateFrom = selection.from,
            dateTo = selection.until
        )
}
