package ge.becrin.fuelcalc.sources.rustata

import ge.becrin.fuelcalc.sources.model.FetchedPrice

data class RustataPage(
    val total: Int,

    val series: Set<String>,

    val facts: List<FetchedPrice>,

    val nextOffset: Int?
)
