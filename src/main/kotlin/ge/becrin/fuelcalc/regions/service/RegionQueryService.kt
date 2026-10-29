package ge.becrin.fuelcalc.regions.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.regions.dto.RegionListDto
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import org.springframework.stereotype.Service

@Service
class RegionQueryService(
    private val archive: ArchiveReadService,

    private val regions: RegionRepository
) {
    fun list(includeEmptyRegions: Boolean = false): RegionListDto =
        archive.read { meta ->
            val selected = if (includeEmptyRegions) regions.findAllOrdered() else regions.findOrdered()

            RegionListDto(meta, selected.map { RegionRefDto(it.okatoCode, it.name) })
        }
}
