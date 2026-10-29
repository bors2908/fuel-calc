package ge.becrin.fuelcalc.fuels.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.fuels.dto.FuelTypeListDto
import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import org.springframework.stereotype.Service

@Service
class FuelTypeQueryService(private val archive: ArchiveReadService) {
    fun list(): FuelTypeListDto =
        archive.read { meta -> FuelTypeListDto(meta, FuelType.entries.map { FuelTypeRefDto(it) }) }
}
