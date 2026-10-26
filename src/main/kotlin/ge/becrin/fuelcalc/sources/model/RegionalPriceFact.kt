package ge.becrin.fuelcalc.sources.model

import java.time.LocalDate
import java.time.Instant
import java.math.BigDecimal

data class RegionalPriceFact(
    val regionCode: String,

    val fuelCode: String,

    val price: BigDecimal,

    val publicationDate: LocalDate? = null,

    val sampleMinStationCount: Int? = null,

    val sampleDayCount: Int? = null,

    val sampleExpectedDayCount: Int? = null,

    val sampleCollectedAt: Instant? = null,

    val sampleInputHash: String? = null
)
