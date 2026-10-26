package ge.becrin.fuelcalc.sources.model

import ge.becrin.fuelcalc.prices.PriceFrequency

data class FeedDefinition(
    val code: String,

    val provider: String,

    val publisher: String,

    val frequency: PriceFrequency,

    val externalCode: String,

    val samplingPolicy: String? = null
)
