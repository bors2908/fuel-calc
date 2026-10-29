package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.regions.model.Region
import ge.becrin.fuelcalc.sources.PriceSource
import java.math.BigDecimal

data class PriceRow(
    val id: Long,

    val region: Region,

    val fuel: FuelType,

    val period: PricePeriod,

    val price: BigDecimal,

    val source: PriceSource
)
