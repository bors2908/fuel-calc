package ge.becrin.fuelcalc.sources.benzup

import java.math.BigDecimal

data class BenzupPrice(
    val regionId: String,

    val regionName: String,

    val fuelCode: String,

    val price: BigDecimal
)
