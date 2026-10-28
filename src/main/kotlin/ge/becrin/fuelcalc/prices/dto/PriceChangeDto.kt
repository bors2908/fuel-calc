package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.PriceChangeStatus

data class PriceChangeDto(
    val status: PriceChangeStatus,

    val startPrice: PriceCellDto,

    val endPrice: PriceCellDto,

    val absoluteRubPerLiter: String?,

    val percent: String?
)
