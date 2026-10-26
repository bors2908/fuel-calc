package ge.becrin.fuelcalc.sources.model



data class PreparedPublication(
    val period: PricePeriod,

    val externalKey: String,

    val url: String,

    val attribution: String,

    val facts: List<RegionalPriceFact>
)
