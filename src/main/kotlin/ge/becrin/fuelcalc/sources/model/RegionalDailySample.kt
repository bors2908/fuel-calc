package ge.becrin.fuelcalc.sources.model

import java.time.Instant
import java.math.BigDecimal

data class RegionalDailySample(
    val regionCode: String,

    val fuelCode: String,

    val price: BigDecimal,

    val stationCount: Int,

    val collectedAt: Instant,

    val oldestPriceAt: Instant,

    val newestPriceAt: Instant,

    val contentHash: String
)
