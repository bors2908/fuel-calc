package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.model.PricePeriod
import java.time.LocalDate

data class PricePeriodDto(
    val key: String,

    val frequency: PriceFrequency,

    val referenceDate: LocalDate
) {
    constructor(period: PricePeriod) : this(period.key, period.frequency, period.referenceDate)
}
