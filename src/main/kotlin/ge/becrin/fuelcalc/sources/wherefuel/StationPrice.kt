package ge.becrin.fuelcalc.sources.wherefuel

import java.math.BigDecimal
import java.time.Instant

data class StationPrice(
    val stationId: String,

    val fuelCode: String,

    val price: BigDecimal,

    val observedAt: Instant
)
