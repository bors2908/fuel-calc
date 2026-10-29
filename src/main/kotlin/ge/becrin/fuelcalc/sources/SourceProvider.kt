package ge.becrin.fuelcalc.sources

enum class SourceProvider(
    val source: PriceSource,

    val displayName: String,

    val url: String
) {
    ROSSTAT(PriceSource.OFFICIAL, "Росстат", "https://showdata.rosstat.gov.ru"),
    RUSTATA(PriceSource.OFFICIAL, "rustata", "https://rustata.ru"),
    BENZUP(PriceSource.MARKET, "BenzUp", "https://benzup.ru/index-region")
}
