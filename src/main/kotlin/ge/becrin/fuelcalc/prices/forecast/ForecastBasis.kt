package ge.becrin.fuelcalc.prices.forecast

import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate

data class ForecastBasis(
    val asOfDate: LocalDate,

    val baseDate: LocalDate,

    val basePriceRubPerLiter: String,

    val historicalYears: List<Int>,

    val growthPercent: String
) {
    val historySource: PriceSource = PriceSource.OFFICIAL

    val method: String = "HISTORICAL_RELATIVE_CHANGE"
}
