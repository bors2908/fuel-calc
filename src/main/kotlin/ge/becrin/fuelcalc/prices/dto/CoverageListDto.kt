package ge.becrin.fuelcalc.prices.dto

import ge.becrin.fuelcalc.api.dto.ApiMetaDto
import ge.becrin.fuelcalc.sources.dto.SourceUpdateDto
import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.sources.PriceSource

data class CoverageListDto(
    val meta: ApiMetaDto,

    val basis: PriceBasis,

    val preferredSource: PriceSource,

    val fallbackOrder: List<PriceSource>,

    val data: List<CoverageDto>,

    val sourceUpdates: Map<String, SourceUpdateDto>
)
