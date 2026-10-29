package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.rosstat.RosstatClient
import ge.becrin.fuelcalc.sources.rustata.RustataClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock

@Service
class OfficialImportService(
    private val rosstat: RosstatClient,

    private val rustata: RustataClient,

    private val http: SourceHttpClient,

    private val store: ImportStore,

    private val catalog: RegionCatalog,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    private val lock = ReentrantLock()

    fun fetch() {
        if (!properties.enabled || !catalog.isReady || !lock.tryLock()) {
            return
        }

        try {
            http.beginRun()

            val today = LocalDate.now(clock.withZone(ZoneId.of(properties.timeZone)))
            var from = maxOf(properties.rosstat.startDate, store.fetchedThrough()?.minusWeeks(1) ?: properties.rosstat.startDate)

            while (from <= today) {
                val until = minOf(from.withMonth(12).withDayOfMonth(31), today)
                val window = FetchWindow(from, until)

                log.info(
                    "Loading data [source={}, provider={}, from={}, until={}]",
                    PriceSource.OFFICIAL, SourceProvider.ROSSTAT, from, until
                )

                val (provider, prices) =
                    try {
                        SourceProvider.ROSSTAT to rosstat.fetch(window)
                    } catch (error: Exception) {
                        log.warn(
                            "Data provider unavailable; trying fallback [source={}, provider={}, fallbackProvider={}, from={}, until={}]",
                            PriceSource.OFFICIAL, SourceProvider.ROSSTAT, SourceProvider.RUSTATA, from, until, error
                        )

                        require(window.from >= properties.rustata.startDate) { "Requested year precedes rustata start date" }

                        try {
                            SourceProvider.RUSTATA to rustata.fetch(window)
                        } catch (fallbackError: Exception) {
                            fallbackError.addSuppressed(error)

                            throw fallbackError
                        }
                    }

                val changed = store.saveWindow(provider, window, prices)

                log.info(
                    "Data loaded [source={}, provider={}, from={}, until={}, prices={}, changed={}]",
                    PriceSource.OFFICIAL, provider, from, until, prices.size, changed
                )

                from = until.plusDays(1)
            }
        } catch (error: Exception) {
            store.failed(PriceSource.OFFICIAL, error)

            log.warn("Data loading failed; saved data retained [source={}]", PriceSource.OFFICIAL, error)
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OfficialImportService::class.java)
    }
}
