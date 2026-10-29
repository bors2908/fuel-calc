package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.regions.model.Region
import java.math.BigDecimal

data class ComparisonRow(
    val region: Region,

    val isBase: Boolean,

    val price: PriceRow?,

    val difference: BigDecimal?,

    val isLowest: Boolean
)
