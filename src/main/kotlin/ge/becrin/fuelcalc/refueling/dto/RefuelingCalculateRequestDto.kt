package ge.becrin.fuelcalc.refueling.dto

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import ge.becrin.fuelcalc.sources.PriceSource

data class RefuelingCalculateRequestDto(
    val selection: RefuelingSelectionDto,

    val volumeLiters: String,

    @param:JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
    val expectedSnapshotIds: Map<PriceSource, String> = emptyMap()
)
