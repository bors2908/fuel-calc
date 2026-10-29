package ge.becrin.fuelcalc.fuels

enum class FuelType(
    val displayName: String,

    val rosstatCode: String,

    val benzupCode: String
) {
    DIESEL("Дизельное топливо", "7804", "D0DT0"),
    AI_92("АИ-92", "7802", "A0920"),
    AI_95("АИ-95", "7803", "A0950")
}
