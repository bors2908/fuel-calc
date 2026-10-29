package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import ge.becrin.fuelcalc.prices.jpa.FuelPriceEntity
import ge.becrin.fuelcalc.prices.jpa.FuelPriceRepository
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Repository
class ImportStore(
    private val archive: ArchiveStateRepository,

    private val states: ImportStateRepository,

    private val regions: RegionRepository,

    private val prices: FuelPriceRepository,

    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun savedRegions(): List<RegionCatalog.Region> =
        regions.findAllOrdered().map { RegionCatalog.Region(it.okatoCode, it.name) }

    @Transactional(readOnly = true)
    fun fetchedThrough(): LocalDate? =
        states.findById(PriceSource.OFFICIAL.name).orElse(null)?.fetchedThrough

    @Transactional
    fun synchronizeRegions(
        catalog: List<RegionCatalog.Region>,
        hash: String
    ): Boolean {
        require(catalog.isNotEmpty()) { "Region catalog must not be empty" }

        archive.lockForPublication()

        val state = state("REGIONS")

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

        state.contentHash = hash
        state.lastSuccessAt = clock.instant()
        state.errorMessage = null
        archive.advanceRevision(clock.instant())

        return true
    }

    @Transactional
    fun saveWindow(
        provider: SourceProvider,
        window: FetchWindow,
        incoming: List<FetchedPrice>
    ): Int {
        val source = provider.source

        require(window.from <= window.until)
        require(incoming.all { it.date in window.from..window.until && it.price.signum() > 0 })

        val unique = incoming.associateBy { Triple(it.regionCode, it.fuel, it.date) }

        require(unique.size == incoming.size) { "Duplicate prices in source window" }

        archive.lockForPublication()

        val existing = prices.findWindow(source, window.from, window.until)
            .associateBy { Triple(it.region.okatoCode, it.fuel, it.referenceDate) }
        val regionCodes = incoming.map { it.regionCode }.distinct()
        val regionByCode = if (regionCodes.isEmpty()) emptyMap() else regions.findSelected(regionCodes).associateBy { it.okatoCode }

        require(regionByCode.size == regionCodes.size) { "Unmapped price region" }

        var changed = 0

        for ((key, fact) in unique) {
            val saved = existing[key]

            if (saved == null) {
                val region = regionByCode.getValue(fact.regionCode)

                prices.save(FuelPriceEntity(
                    region = region,
                    fuel = fact.fuel,
                    source = source,
                    frequency = source.frequency,
                    provider = provider,
                    referenceDate = fact.date,
                    priceRubPerLiter = fact.price,
                    publicationDate = fact.publicationDate,
                    loadedAt = clock.instant()
                ))

                if (!region.hasData) {
                    region.hasData = true
                }

                changed++
            } else if (saved.publicationDate == null || fact.publicationDate == null || fact.publicationDate >= saved.publicationDate) {
                if (saved.priceRubPerLiter.compareTo(fact.price) != 0 || saved.provider != provider ||
                    saved.publicationDate != fact.publicationDate
                ) {
                    saved.priceRubPerLiter = fact.price
                    saved.provider = provider
                    saved.publicationDate = fact.publicationDate
                    saved.loadedAt = clock.instant()
                    changed++
                }
            }
        }

        val state = state(source.name)
        state.fetchedThrough = maxOf(state.fetchedThrough ?: window.until, window.until)
        state.lastSuccessAt = clock.instant()
        state.lastAttemptAt = clock.instant()
        state.errorMessage = null

        if (changed > 0) {
            archive.advanceRevision(clock.instant())
        }

        return changed
    }

    @Transactional
    fun failed(
        source: PriceSource,
        error: Exception
    ) {
        archive.lockForPublication()

        val state = state(source.name)
        state.lastAttemptAt = clock.instant()
        state.errorMessage = error.message?.take(2000) ?: error.javaClass.simpleName
    }

    private fun state(code: String): ImportStateEntity =
        states.findById(code).orElseGet { states.save(ImportStateEntity(code = code)) }
}
