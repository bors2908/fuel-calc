package ge.becrin.fuelcalc.sources.wherefuel

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyRepository
import ge.becrin.fuelcalc.prices.service.RegionalPricePublicationService
import ge.becrin.fuelcalc.prices.service.RegionalPriceSampleService
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock

@Service
class WhereFuelImportService(
    private val client: WhereFuelClient,

    private val sampler: RegionalStationSampler,

    private val samples: RegionalPriceSampleService,

    private val publisher: RegionalPricePublicationService,

    private val store: ImportStore,

    private val keys: RegionalPriceKeyRepository,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    private val lock = ReentrantLock()

    fun initialize() {
        store.initialize(PriceFrequency.entries.map(::definition))
    }

    fun fetch() {
        if (!properties.enabled || !properties.wherefuel.enabled || !lock.tryLock()) {
            return
        }

        val runs = mutableMapOf<PriceFrequency, Long>()
        val counts = mutableMapOf<PriceFrequency, Int>()
        var failure: SourceImportException? = null

        try {
            for (frequency in PriceFrequency.entries) {
                store.begin(definition(frequency))?.let { runs[frequency] = it }
            }

            if (runs.isEmpty()) {
                return
            }

            client.beginRun()
            val deadline = clock.instant().plus(properties.maxRunDuration)

            for (boundary in client.boundaries()) {
                if (clock.instant().isAfter(deadline)) {
                    throw SourceImportException("RUN_BUDGET", "WhereFuel run duration budget exhausted")
                }

                val prepared = sampler.sample(boundary.regionCode, client.fetch(boundary), clock.instant())
                samples.append(runs.values.first(), prepared)
            }
        } catch (error: SourceImportException) {
            failure = error
        } catch (error: Exception) {
            failure = SourceImportException("IMPORT_FAILED", "WhereFuel import or validation failed: ${error.message}", cause = error)
        } finally {
            try {
                for ((frequency, runId) in runs) {
                    var feedFailure = failure

                    try {
                        val today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow")))

                        for (publication in samples.prepare(frequency, today, properties.wherefuel.baseUrl)) {
                            val result = publisher.publish(definition(frequency), runId, publication)
                            counts[frequency] = counts.getOrDefault(frequency, 0) + result.inserted

                            if (result.conflicts > 0) {
                                throw SourceImportException("UNRESOLVED_REVISION", "Conflicting calculated revision")
                            }
                        }
                    } catch (error: Exception) {
                        feedFailure = error as? SourceImportException
                            ?: SourceImportException("PUBLISH_FAILED", "Sample publication failed: ${error.message}", cause = error)
                    }

                    val latest = keys.latestDate("WHEREFUEL", frequency)
                    val maxAge = if (frequency == PriceFrequency.WEEKLY) 14L else 62L
                    val freshness =
                        when {
                            latest == null -> "UNKNOWN"
                            latest.isBefore(LocalDate.now(clock).minusDays(maxAge)) -> "DELAYED"
                            else -> "READY"
                        }

                    store.finish(runId, counts.getOrDefault(frequency, 0), feedFailure, freshness)

                    if (feedFailure == null) {
                        log.info(
                            "Source import finished [feedCode={}, runId={}, inserted={}, latest={}]",
                            definition(frequency).code, runId, counts.getOrDefault(frequency, 0), latest
                        )
                    } else {
                        log.warn(
                            "Source import failed [feedCode={}, runId={}, inserted={}, errorCode={}, retryAt={}, message={}]",
                            definition(frequency).code, runId, counts.getOrDefault(frequency, 0),
                            feedFailure.code, feedFailure.retryAt, feedFailure.message, feedFailure
                        )
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    private fun definition(frequency: PriceFrequency): FeedDefinition =
        FeedDefinition(
            "WHEREFUEL_${frequency}_TOP20_V1", "WHEREFUEL", "WHEREFUEL", frequency,
            "${properties.wherefuel.baseUrl}/api/stations", RegionalStationSampler.POLICY
        )

    companion object {
        private val log = LoggerFactory.getLogger(WhereFuelImportService::class.java)
    }
}
