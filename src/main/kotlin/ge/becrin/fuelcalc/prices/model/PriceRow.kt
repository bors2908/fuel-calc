package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceSource
import java.math.BigDecimal

data class PriceRow(
    val id: Long,

    val region: RegionRefDto,

    val fuel: FuelTypeRefDto,

    val period: PricePeriodDto,

    val price: BigDecimal,

    val source: PriceSource,

    val version: Long = 0
)
