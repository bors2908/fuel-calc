package ge.becrin.fuelcalc.prices.model

import ge.becrin.fuelcalc.sources.PriceSource

data class PriceReadContext(
    val selection: PriceSelection,

    val source: PriceSource,

    val bounds: Map<String, SeriesBounds>
)
