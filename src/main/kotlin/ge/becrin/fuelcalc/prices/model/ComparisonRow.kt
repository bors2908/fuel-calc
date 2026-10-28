package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import java.math.BigDecimal

data class ComparisonRow(
    val region: RegionRefDto,

    val isBase: Boolean,

    val price: PriceRow?,

    val difference: BigDecimal?,

    val isLowest: Boolean,

    val isHighest: Boolean
)
