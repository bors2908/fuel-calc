package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import ge.becrin.fuelcalc.prices.jpa.FuelPriceEntity
import ge.becrin.fuelcalc.prices.jpa.FuelPriceRepository
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import java.time.Clock
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class PriceImportStore(
    private val archive: ArchiveStateRepository,

    private val states: ImportStateStore,

    private val regions: RegionRepository,

    private val prices: FuelPriceRepository,

    private val clock: Clock
) {
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

        val now = clock.instant()
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
                    loadedAt = now
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
                    saved.loadedAt = now
                    changed++
                }
            }
        }

        val state = states.source(source)
        state.fetchedThrough = maxOf(state.fetchedThrough ?: window.until, window.until)
        state.lastSuccessAt = now
        state.lastAttemptAt = now
        state.errorMessage = null

        if (changed > 0) {
            archive.advanceRevision(now)
        }

        return changed
    }
}
