package ge.becrin.fuelcalc.sources.model

import ge.becrin.fuelcalc.prices.PriceFrequency
import java.time.LocalDate

data class PricePeriod(
    val frequency: PriceFrequency,

    val referenceDate: LocalDate,

    val startDate: LocalDate? = null,

    val endDate: LocalDate? = null
) {
    val key: String
        get() = "$frequency:$referenceDate"
}
