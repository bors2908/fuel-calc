package ge.becrin.fuelcalc.sources.dto

import ge.becrin.fuelcalc.sources.SourceProvider

data class SourceRefDto(
    val code: String,

    val name: String,

    val url: String?
) {
    constructor(provider: SourceProvider) : this(provider.name, provider.displayName, provider.url)
}
