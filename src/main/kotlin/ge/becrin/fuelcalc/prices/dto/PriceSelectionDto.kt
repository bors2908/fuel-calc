package ge.becrin.fuelcalc.prices.dto

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import ge.becrin.fuelcalc.api.web.IsoDateDeserializer
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceSource
import tools.jackson.databind.annotation.JsonDeserialize
import java.time.LocalDate

data class PriceSelectionDto(
    val baseRegionCode: String,

    @param:JsonSetter(contentNulls = Nulls.FAIL)
    val regionCodes: List<String>,

    val fuelType: FuelType,

    @param:JsonDeserialize(using = IsoDateDeserializer::class)
    val dateFrom: LocalDate,

    @param:JsonDeserialize(using = IsoDateDeserializer::class)
    val dateTo: LocalDate,

    val frequency: PriceFrequency,

    val source: PriceSource
)
