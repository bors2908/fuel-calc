package ge.becrin.fuelcalc.fuels.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.fuels.dto.FuelTypeListDto
import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.fuels.jpa.FuelTypeRepository
import org.springframework.stereotype.Service

@Service
class FuelTypeQueryService(
    private val archive: ArchiveReadService,

    private val fuels: FuelTypeRepository
) {
    fun list(): FuelTypeListDto =
        archive.read { meta ->
            FuelTypeListDto(meta, fuels.findOrdered().map { FuelTypeRefDto(FuelType.valueOf(it.code), it.name) })
        }
}
