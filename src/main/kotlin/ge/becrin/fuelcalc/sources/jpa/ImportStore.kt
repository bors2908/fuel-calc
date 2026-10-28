package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.sources.ImportRunStatus
import ge.becrin.fuelcalc.sources.PriceAggregationMethod
import ge.becrin.fuelcalc.sources.SourceUpdateStatus
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceImportException
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Repository
class ImportStore(
    private val entityManager: EntityManager,

    private val archive: ArchiveStateRepository,

    private val feeds: SourceFeedRepository,

    private val runs: ImportRunRepository,

    private val states: SourceFetchStateRepository,

    private val regions: RegionRepository,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    @Transactional
    fun initialize(definitions: List<FeedDefinition>) {
        lockArchive()

        val configured = feeds.findAll().associateBy { it.code }.toMutableMap()
        val existingStates = states.findAll().map { it.feedId }.toSet()

        for (definition in definitions) {
            if (definition.code !in configured) {
                val feed = feeds.save(
                    SourceFeedEntity(
                        code = definition.code,
                        publisher = entityManager.getReference(DataSourceEntity::class.java, definition.publisher),
                        provider = entityManager.getReference(DataSourceEntity::class.java, definition.provider),
                        externalCode = definition.externalCode,
                        frequency = definition.frequency,
                        aggregationMethod =
                            if (definition.samplingPolicy == null) PriceAggregationMethod.OFFICIAL_STATISTICS
                            else PriceAggregationMethod.STATION_SAMPLE_MEAN,
                        samplingPolicyCode = definition.samplingPolicy
                    )
                )

                configured[feed.code] = feed
            }
        }

        for (feed in configured.values) {
            if (feed.id !in existingStates) {
                entityManager.persist(SourceFetchStateEntity(feed = feed, updatedAt = now()))
            }
        }

        runs.interruptRunning(now())
    }

    @Transactional(readOnly = true)
    fun savedRegions(): List<RegionCatalog.Region> =
        regions.findAll().sortedBy { it.code }.map { RegionCatalog.Region(it.code, it.name) }

    @Transactional
    fun synchronizeRegions(catalog: List<RegionCatalog.Region>) {
        require(catalog.isNotEmpty()) { "Region catalog must not be empty" }

        lockArchive()

        var changed = false

        for (chunk in catalog.chunked(properties.batchSize)) {
            val existing = regions.findSelected(chunk.map { it.code }).associateBy { it.code }.toMutableMap()

            for (region in chunk) {
                val entity = existing[region.code]

                if (entity == null) {
                    existing[region.code] = regions.save(RegionEntity(code = region.code, name = region.name, createdAt = now()))
                    changed = true
                } else if (entity.name != region.name) {
                    entity.name = region.name
                    changed = true
                }
            }
        }

        if (changed) {
            bumpRevision()
        }
    }

    @Transactional
    fun begin(feed: FeedDefinition): Long? {
        val state = states.findReady(feed.code, now()) ?: return null

        return runs.save(ImportRunEntity(feed = state.feed, startedAt = now())).id
    }

    @Transactional
    fun finish(
        runId: Long,
        count: Int,
        error: SourceImportException?,
        freshness: String
    ) {
        lockArchive()

        val run = checkNotNull(runs.findWithFeed(runId))
        val timestamp = now()

        if (run.status == ImportRunStatus.RUNNING) {
            run.status = if (error == null) ImportRunStatus.SUCCEEDED else ImportRunStatus.FAILED
            run.finishedAt = timestamp
            run.importedCount = count
            run.errorCode = error?.code
            run.errorMessage = error?.message
        }

        run.feed.updateStatus = if (error == null) SourceUpdateStatus.valueOf(freshness) else SourceUpdateStatus.UNAVAILABLE

        if (error == null) {
            run.feed.lastSuccessAt = timestamp
        }

        error?.retryAt?.let { retryAt ->
            for (state in states.findForProvider(run.feed.provider.code)) {
                state.nextFetchNotBefore = retryAt
                state.updatedAt = timestamp
            }
        }

        bumpRevision()
    }

    @Transactional(readOnly = true)
    fun state(feed: FeedDefinition): SourceFetchStateEntity =
        checkNotNull(states.findByFeedCode(feed.code))

    @Transactional(readOnly = true)
    fun lastSuccess(feed: FeedDefinition): Instant? =
        feeds.lastSuccess(feed.code)

    @Transactional
    fun saveProgress(
        feed: FeedDefinition,
        mode: String,
        next: LocalDate?,
        until: LocalDate?
    ) {
        val state = state(feed)
        val timestamp = now()
        state.updatedAt = timestamp

        if (mode == "BACKFILL") {
            state.backfillNextFrom = next
            state.backfillUntil = until
            state.backfillCompletedAt = if (next == null) timestamp else null
        } else {
            state.reconciliationNextFrom = next
            state.reconciliationUntil = until

            if (next == null) {
                state.lastReconciledAt = timestamp
            }
        }
    }

    @Transactional
    fun lockArchive() {
        archive.lockForPublication()
    }

    @Transactional
    fun bumpRevision() {
        archive.advanceRevision(now())
    }

    fun now(): Instant =
        clock.instant()
}
