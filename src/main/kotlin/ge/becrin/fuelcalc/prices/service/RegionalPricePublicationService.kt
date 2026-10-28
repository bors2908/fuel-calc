package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.fuels.jpa.FuelTypeRepository
import ge.becrin.fuelcalc.prices.jpa.PricePeriodEntity
import ge.becrin.fuelcalc.prices.jpa.PricePeriodRepository
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyRepository
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceObservationEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceObservationRepository
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.sources.jpa.ImportRunRepository
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.jpa.SourcePublicationEntity
import ge.becrin.fuelcalc.sources.jpa.SourcePublicationRepository
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PublicationResult
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.ImportHash
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RegionalPricePublicationService(
    private val store: ImportStore,

    private val runs: ImportRunRepository,

    private val regions: RegionRepository,

    private val fuels: FuelTypeRepository,

    private val periods: PricePeriodRepository,

    private val publications: SourcePublicationRepository,

    private val keys: RegionalPriceKeyRepository,

    private val observations: RegionalPriceObservationRepository
) {
    @Transactional
    fun publish(
        feed: FeedDefinition,
        runId: Long,
        publication: PreparedPublication
    ): PublicationResult {
        val period = publication.period
        require(period.frequency == feed.frequency)

        val facts = publication.facts.distinct().sortedWith(compareBy({ it.regionCode }, { it.fuelCode }))

        if (facts.isEmpty()) {
            throw SourceImportException("EMPTY_PUBLICATION", "Publication has no regional prices")
        }

        if (facts.groupBy { it.regionCode to it.fuelCode }.any { it.value.size != 1 }) {
            throw SourceImportException("SOURCE_DATA_CONFLICT", "Conflicting values in one publication")
        }

        for (fact in facts) {
            require(fact.price.signum() >= 0 && fact.price.toPlainString().length <= 64)
            require(fact.fuelCode in setOf("AI_92", "AI_95", "DIESEL"))
        }

        store.lockArchive()

        val run = checkNotNull(runs.findWithFeed(runId))
        require(run.feed.code == feed.code) { "Import run belongs to another feed" }

        val regionCodes = facts.map { it.regionCode }.distinct()
        val regionByCode = regions.findSelected(regionCodes).associateBy { it.code }
        require(regionByCode.size == regionCodes.size) { "Unmapped region code" }

        val savedPeriod = periods.findPeriod(period.frequency, period.referenceDate)
            ?: periods.save(
                PricePeriodEntity(
                    periodKey = period.key,
                    frequency = period.frequency,
                    referenceDate = period.referenceDate,
                    startDate = period.startDate,
                    endDate = period.endDate
                )
            )
        require(savedPeriod.startDate == period.startDate && savedPeriod.endDate == period.endDate) { "Period boundary mismatch" }

        val drafts = facts.groupBy { it.publicationDate }.map { (date, rows) ->
            val hash = ImportHash.hash(
                listOf("REGIONAL_V1", feed.code, publication.externalKey, period, publication.url, publication.attribution, date) +
                    rows.flatMap { factFields(it) }
            )

            SourcePublicationEntity(
                feed = run.feed,
                firstImportRun = run,
                externalKey = publication.externalKey,
                contentHash = hash,
                publicationDate = date,
                loadedAt = store.now(),
                publicationUrl = publication.url,
                attribution = publication.attribution
            )
        }
        val editions = publications.findEditions(run.feed, publication.externalKey, drafts.map { it.contentHash })
            .associateBy { it.contentHash }.toMutableMap()

        for (draft in drafts) {
            if (draft.contentHash !in editions) {
                editions[draft.contentHash] = publications.save(draft)
            }
        }

        val publicationByDate = drafts.associate { it.publicationDate to editions.getValue(it.contentHash) }
        val priceKeys = keys.findForPublication(savedPeriod, feed.provider, regionByCode.values)
            .associateBy { it.region.code to it.fuelType.code }.toMutableMap()

        for (fact in facts) {
            val cell = fact.regionCode to fact.fuelCode

            if (cell !in priceKeys) {
                priceKeys[cell] = keys.save(
                    RegionalPriceKeyEntity(
                        region = regionByCode.getValue(fact.regionCode),
                        fuelType = fuels.getReferenceById(fact.fuelCode),
                        period = savedPeriod,
                        provider = run.feed.provider,
                        createdAt = store.now()
                    )
                )
            }
        }

        val existing = observations.findPublished(editions.values, priceKeys.values)
        val published = existing.associateBy { it.publication.id to it.priceKey.id }
        val inserted = facts.mapNotNull { fact ->
            val priceKey = priceKeys.getValue(fact.regionCode to fact.fuelCode)
            val edition = publicationByDate.getValue(fact.publicationDate)

            if ((edition.id to priceKey.id) in published) {
                null
            } else {
                RegionalPriceObservationEntity(
                    priceKey = priceKey,
                    publication = edition,
                    priceRubPerLiter = fact.price,
                    sampleMinStationCount = fact.sampleMinStationCount,
                    sampleDayCount = fact.sampleDayCount,
                    sampleExpectedDayCount = fact.sampleExpectedDayCount,
                    sampleCollectedAt = fact.sampleCollectedAt
                )
            }
        }
        val versions = existing + observations.saveAll(inserted)
        var conflicts = 0
        var changed = false

        for (version in versions) {
            val priceKey = version.priceKey
            val current = priceKey.currentObservation
            val candidateDate = version.publication.publicationDate
            val currentDate = current?.publication?.publicationDate
            val newer =
                if (feed.samplingPolicy != null) {
                    val candidateAt = version.sampleCollectedAt
                    val currentAt = current?.sampleCollectedAt

                    candidateAt != null && (currentAt == null || candidateAt > currentAt)
                } else {
                    candidateDate != null && currentDate != null && candidateDate > currentDate
                }

            if (current == null || newer) {
                priceKey.currentObservation = version
                changed = true
            } else if (version.id != current.id && version.priceRubPerLiter.compareTo(current.priceRubPerLiter) != 0) {
                val older = candidateDate != null && currentDate != null && candidateDate < currentDate

                if (!older) {
                    conflicts++
                }
            }
        }

        if (inserted.isNotEmpty() || changed) {
            store.bumpRevision()
        }

        return PublicationResult(inserted.size, conflicts)
    }

    private fun factFields(fact: RegionalPriceFact): List<Any?> =
        listOf(
            fact.regionCode, fact.fuelCode, fact.price, fact.publicationDate, fact.sampleMinStationCount,
            fact.sampleDayCount, fact.sampleExpectedDayCount, fact.sampleCollectedAt, fact.sampleInputHash
        )
}
