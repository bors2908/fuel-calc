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
    fun list(): RegionListDto =
        archive.read { meta ->
            RegionListDto(meta, regions.findOrdered().map { RegionRefDto(it.code, it.name) })
        }
}
