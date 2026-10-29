package ge.becrin.fuelcalc.sources.model

import ge.becrin.fuelcalc.fuels.FuelType
import java.math.BigDecimal
import java.time.LocalDate

data class FetchedPrice(
    val regionCode: String,

    val fuel: FuelType,

    val date: LocalDate,

    val price: BigDecimal,

    val publicationDate: LocalDate? = null
)
