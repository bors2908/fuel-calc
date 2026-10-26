package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.service.RegionalPricePublicationService
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.FetchWindow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.sql.Date
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Service
class RegionalImportService(
    private val adapters: List<RegionalSourceAdapter>,

    private val store: ImportStore,

    private val publisher: RegionalPricePublicationService,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun initialize() {
        require(properties.batchSize in 1..1000)
        require(properties.historyWindowsPerRun in 1..52)
        require(properties.historyFrom <= LocalDate.now(clock))
        require(properties.maxResponseBytes in 1024..(64 * 1024 * 1024))

        store.initialize(adapters.flatMap { adapter -> PriceFrequency.entries.map(adapter::definition) })
    }

    fun fetch(
        provider: String,
        forceMonthly: Boolean = false
    ) {
        val adapter = adapters.single { it.provider == provider }
        val lock = locks.computeIfAbsent(provider) { ReentrantLock() }

        if (!properties.enabled || !adapter.enabled || !lock.tryLock()) {
            return
        }

        try {
            for (frequency in PriceFrequency.entries) {
                val feed = adapter.definition(frequency)
                val lastSuccess = store.jdbc.queryForList(
                    "SELECT last_success_at FROM source_feeds WHERE code = :code", mapOf("code" to feed.code)
                ).singleOrNull()?.get("last_success_at") as? Timestamp

                if (forceMonthly || frequency == PriceFrequency.WEEKLY || lastSuccess == null ||
                    lastSuccess.toInstant().plus(
                        if (provider == "ROSSTAT") properties.rosstat.monthlyInterval else properties.rustata.monthlyInterval
                    ).isBefore(clock.instant())
                ) {
                    fetchFeed(adapter, feed)
                }
            }
        } finally {
            lock.unlock()
        }
    }

    private fun fetchFeed(
        adapter: RegionalSourceAdapter,
        feed: FeedDefinition
    ) {
        val runId = store.begin(feed) ?: return
        val today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow")))
        val deadline = clock.instant().plus(properties.maxRunDuration)
        var imported = 0
        var latest: LocalDate? = null
        var failure: SourceImportException? = null
        var activeWindow: FetchWindow? = null
        var stage = "PREPARE"
        val started = System.nanoTime()

        log.info("Source import started [feedCode={}, runId={}, provider={}]", feed.code, runId, feed.provider)

        try {
            adapter.beginRun()

            val recentFrom =
                if (feed.frequency == PriceFrequency.WEEKLY) today.minusWeeks(8)
                else today.minusMonths(3).withDayOfMonth(1)
            val recent = FetchWindow(maxOf(recentFrom, properties.historyFrom), today, "INCREMENTAL")
            val windows = mutableListOf(recent)
            val state = store.state(feed)
            val backfillComplete = state["backfill_completed_at"] != null
            val lastReconciled = (state["last_reconciled_at"] as? Timestamp)?.toInstant()
            val mode = if (backfillComplete) "RECONCILIATION" else "BACKFILL"
            val prefix = if (backfillComplete) "reconciliation" else "backfill"
            val due = !backfillComplete || state["reconciliation_next_from"] != null || lastReconciled == null ||
                lastReconciled.plus(properties.reconciliationInterval).isBefore(clock.instant())

            if (due) {
                var from = (state["${prefix}_next_from"] as? Date)?.toLocalDate() ?: properties.historyFrom
                val until = (state["${prefix}_until"] as? Date)?.toLocalDate() ?: today

                if (!from.isAfter(until)) {
                    store.saveProgress(feed, mode, from, until)

                    repeat(properties.historyWindowsPerRun) {
                        if (!from.isAfter(until)) {
                            val end =
                                if (feed.frequency == PriceFrequency.WEEKLY) minOf(from.plusWeeks(8).minusDays(1), until)
                                else minOf(from.plusYears(1).minusDays(1), until)

                            windows.add(FetchWindow(from, end, mode))
                            from = end.plusDays(1)
                        }
                    }
                }
            }

            for (window in windows) {
                activeWindow = window

                if (clock.instant().isAfter(deadline)) {
                    break
                }

                stage = "FETCH"

                val prepared = adapter.fetch(feed, window)

                log.info(
                    "Source window fetched [feedCode={}, runId={}, mode={}, from={}, until={}, publications={}, facts={}]",
                    feed.code, runId, window.mode, window.from, window.until, prepared.size, prepared.sumOf { it.facts.size }
                )

                stage = "PUBLISH"

                for (publication in prepared) {
                    val result = publisher.publish(feed, runId, publication)
                    imported += result.inserted
                    latest = maxOf(latest ?: publication.period.referenceDate, publication.period.referenceDate)

                    if (result.conflicts > 0) {
                        throw SourceImportException("UNRESOLVED_REVISION", "Revision order cannot be established; current prices retained")
                    }
                }

                if (window.mode != "INCREMENTAL") {
                    stage = "SAVE_PROGRESS"

                    val fixedUntil = store.state(feed)[if (window.mode == "BACKFILL") "backfill_until" else "reconciliation_until"]
                    val until = (fixedUntil as Date).toLocalDate()
                    val next = window.until.plusDays(1).takeUnless { it.isAfter(until) }

                    store.saveProgress(feed, window.mode, next, if (next == null) null else until)
                }
            }
        } catch (error: SourceImportException) {
            failure = error
        } catch (error: Exception) {
            failure = SourceImportException("IMPORT_FAILED", "Source import or validation failed: ${error.message}", cause = error)
        }

        val maxAge = if (feed.frequency == PriceFrequency.WEEKLY) 14L else 62L
        val freshness =
            when {
                latest == null -> "UNKNOWN"
                latest.isBefore(today.minusDays(maxAge)) -> "DELAYED"
                else -> "READY"
            }

        store.finish(runId, imported, failure, freshness)

        val duration = Duration.ofNanos(System.nanoTime() - started).toMillis()

        if (failure == null) {
            log.info(
                "Source import finished [feedCode={}, runId={}, inserted={}, latest={}, durationMs={}]",
                feed.code, runId, imported, latest, duration
            )
        } else {
            log.warn(
                "Source import failed [feedCode={}, runId={}, stage={}, mode={}, from={}, until={}, " +
                    "inserted={}, errorCode={}, retryAt={}, durationMs={}, message={}]",
                feed.code, runId, stage, activeWindow?.mode, activeWindow?.from, activeWindow?.until,
                imported, failure.code, failure.retryAt, duration, failure.message, failure
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RegionalImportService::class.java)
    }
}
