package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import ge.becrin.fuelcalc.prices.jpa.FuelPriceRepository
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.regions.model.Region
import java.time.Clock
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class RegionImportStore(
    private val archive: ArchiveStateRepository,

    private val states: ImportStateStore,

    private val regions: RegionRepository,

    private val prices: FuelPriceRepository,

    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun savedRegions(): List<Region> =
        regions.findAllOrdered().map { Region(it.okatoCode, it.name) }

    @Transactional
    fun synchronizeRegions(
        catalog: List<Region>,
        hash: String
    ): Boolean {
        require(catalog.isNotEmpty()) { "Region catalog must not be empty" }

        archive.lockForPublication()

        val state = states.regions()

        if (state.contentHash == hash) {
            return false
        }

        val incoming = catalog.associateBy { it.okatoCode }
        val existing = regions.findAll().associateBy { it.okatoCode }

        prices.deleteOutsideCatalog(incoming.keys)
        regions.deleteAll(existing.filterKeys { it !in incoming }.values)

        for ((code, entry) in incoming) {
            val saved = existing[code]

            if (saved == null) {
                regions.save(RegionEntity(okatoCode = code, name = entry.name))
            } else if (saved.name != entry.name) {
                saved.name = entry.name
            }
        }

        val now = clock.instant()

        state.contentHash = hash
        state.lastSuccessAt = now
        state.errorMessage = null
        archive.advanceRevision(now)

        return true
    }
}
