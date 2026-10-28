package ge.becrin.fuelcalc.refueling.dto

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import ge.becrin.fuelcalc.prices.dto.PriceSelectionDto

data class RefuelingCalculateRequestDto(
    val selection: PriceSelectionDto,

    val volumeLiters: String,

    @param:JsonSetter(nulls = Nulls.FAIL)
    val expectedSnapshotId: String? = null
)
