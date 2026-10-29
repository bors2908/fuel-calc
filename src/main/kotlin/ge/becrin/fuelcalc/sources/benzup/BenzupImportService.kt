package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyRepository
import ge.becrin.fuelcalc.prices.service.RegionalPricePublicationService
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock

@Service
class BenzupImportService(
    private val client: BenzupClient,

    private val archive: BenzupArchiveService,

    private val publisher: RegionalPricePublicationService,

    private val store: ImportStore,

    private val catalog: RegionCatalog,

    private val keys: RegionalPriceKeyRepository,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    private val lock = ReentrantLock()

    fun initialize() {
        store.initialize(PriceFrequency.entries.map(::definition))
    }

    fun fetch() {
        if (!properties.enabled || !properties.benzup.enabled || !lock.tryLock()) {
            return
        }

        val runs = mutableMapOf<PriceFrequency, Long>()
        var failure: SourceImportException? = null

        try {
            for (frequency in PriceFrequency.entries) {
                store.begin(definition(frequency))?.let { runs[frequency] = it }
            }

            val dailyRun = runs[PriceFrequency.DAILY] ?: return
            val incoming = client.fetch()
            val collectedAt = clock.instant()
            val missing = incoming.distinctBy { it.regionId }.filter { resolve(it.regionName) == null }

            if (missing.isNotEmpty()) {
                store.synchronizeRegions(missing.map { RegionCatalog.Region("BENZUP_${it.regionId}", it.regionName) })
                catalog.replace(store.savedRegions())
            }

            val prices = incoming.associate { row ->
                (checkNotNull(resolve(row.regionName)).code to row.fuelCode) to row.price
            }
            require(prices.size == incoming.size) { "Ambiguous BenzUp region mapping" }

            archive.record(dailyRun, prices, collectedAt)

            log.info(
                "BenzUp snapshot collected [runId={}, regions={}, prices={}, collectedAt={}]",
                dailyRun, prices.keys.map { it.first }.distinct().size, prices.size, collectedAt
            )
        } catch (error: Exception) {
            failure = error as? SourceImportException
                ?: SourceImportException("IMPORT_FAILED", "BenzUp import failed: ${error.message}", cause = error)
        } finally {
            try {
                for ((frequency, runId) in runs) {
                    var feedFailure = failure
                    var inserted = 0

                    if (feedFailure == null) {
                        try {
                            val feed = definition(frequency)
                            val today = LocalDate.now(clock.withZone(BenzupArchiveService.ZONE))
                            val since = store.lastSuccess(feed)?.atZone(BenzupArchiveService.ZONE)?.toLocalDate()

                            for (publication in archive.prepare(frequency, today, since, properties.benzup.endpoint)) {
                                val result = publisher.publish(feed, runId, publication)
                                inserted += result.inserted

                                if (result.conflicts > 0) {
                                    throw SourceImportException("UNRESOLVED_REVISION", "Conflicting BenzUp aggregate revision")
                                }
                            }
                        } catch (error: Exception) {
                            feedFailure = error as? SourceImportException
                                ?: SourceImportException("PUBLISH_FAILED", "BenzUp publication failed: ${error.message}", cause = error)
                        }
                    }

                    val latest = keys.latestDate("BENZUP", frequency)
                    val freshness = if (latest == null) "UNKNOWN" else "READY"
                    store.finish(runId, inserted, feedFailure, freshness)

                    if (feedFailure == null) {
                        log.info(
                            "BenzUp import finished [frequency={}, runId={}, inserted={}, latest={}]",
                            frequency, runId, inserted, latest
                        )
                    } else {
                        log.warn(
                            "BenzUp import failed [frequency={}, runId={}, errorCode={}]",
                            frequency, runId, feedFailure.code, feedFailure
                        )
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    fun definition(frequency: PriceFrequency): FeedDefinition =
        FeedDefinition(
            code = "BENZUP_${frequency}_V1",
            provider = "BENZUP",
            publisher = "BENZUP",
            frequency = frequency,
            externalCode = properties.benzup.endpoint,
            samplingPolicy = BenzupArchiveService.POLICY
        )

    private fun resolve(name: String): RegionCatalog.Region? =
        catalog.byName(name)
            ?: if (name == "Ханты-Мансийский автономный округ") catalog.byName("$name - Югра") else null

    companion object {
        private val log = LoggerFactory.getLogger(BenzupImportService::class.java)
    }
}
