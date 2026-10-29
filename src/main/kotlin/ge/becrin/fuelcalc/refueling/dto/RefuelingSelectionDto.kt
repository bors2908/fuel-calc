package ge.becrin.fuelcalc.refueling.dto

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import ge.becrin.fuelcalc.api.web.IsoDateDeserializer
import ge.becrin.fuelcalc.fuels.FuelType
import tools.jackson.databind.annotation.JsonDeserialize
import java.time.LocalDate

data class RefuelingSelectionDto(
    val baseRegionCode: String,

    @param:JsonSetter(contentNulls = Nulls.FAIL)
    val regionCodes: List<String>,

    val fuelType: FuelType,

    @param:JsonDeserialize(using = IsoDateDeserializer::class)
    val date: LocalDate
)
