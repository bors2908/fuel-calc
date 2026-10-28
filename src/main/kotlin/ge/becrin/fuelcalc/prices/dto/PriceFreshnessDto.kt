package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonProperty
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto
import java.time.LocalDate

data class PriceFreshnessDto(
    val latestAvailableDate: LocalDate?,

    @get:JsonProperty("isLatestAvailable")
    val isLatestAvailable: Boolean?,

    val sourceUpdate: SourceUpdateDto
)
