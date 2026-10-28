package ge.becrin.fuelcalc.prices.dto

import java.time.Instant

data class RegionalPriceSampleDto(
    val policyCode: String,

    val minStationCount: Int,

    val dayCount: Int,

    val expectedDayCount: Int,

    val collectedAt: Instant
)
