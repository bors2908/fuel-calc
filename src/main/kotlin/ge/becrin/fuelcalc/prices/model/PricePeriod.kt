package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.prices.PriceFrequency
import java.time.LocalDate

data class PricePeriod(
    val frequency: PriceFrequency,

    val referenceDate: LocalDate
) {
    val key: String
        get() =
            "$frequency:$referenceDate"
}
