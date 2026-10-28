package ge.becrin.fuelcalc.regions.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.regions.dto.RegionListDto
import ge.becrin.fuelcalc.regions.dto.RegionQueryDto
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import org.springframework.stereotype.Service

@Service
class RegionQueryService(
    private val archive: ArchiveReadService,

    private val regions: RegionRepository
) {
    fun list(query: RegionQueryDto): RegionListDto =
        archive.read { meta ->
            val text = query.filterName.orEmpty().trim().replace("!", "!!").replace("%", "!%").replace("_", "!_")
            val data = regions.findMatching("%$text%").map { RegionRefDto(it.code, it.name) }

            RegionListDto(meta, data)
        }
}
