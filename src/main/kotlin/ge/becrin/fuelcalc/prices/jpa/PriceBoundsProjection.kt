package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate

interface PriceBoundsProjection {
    val source: PriceSource

    val regionCode: String

    val firstDate: LocalDate

    val lastDate: LocalDate
}
