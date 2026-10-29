package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.fuels.FuelType
import java.math.BigDecimal

data class BenzupPrice(
    val regionId: String,

    val fuel: FuelType,

    val price: BigDecimal
)
