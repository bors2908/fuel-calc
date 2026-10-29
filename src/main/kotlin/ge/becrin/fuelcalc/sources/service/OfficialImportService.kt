package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.config.BusinessCalendar
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStateStore
import ge.becrin.fuelcalc.sources.jpa.PriceImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.rosstat.RosstatClient
import ge.becrin.fuelcalc.sources.rosstat.RosstatReportDefinition
import ge.becrin.fuelcalc.sources.rustata.RustataClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OfficialImportService(
    private val rosstat: RosstatClient,

    private val rustata: RustataClient,

    private val runner: ImportRunner,

    private val states: ImportStateStore,

    private val store: PriceImportStore,

    private val properties: ImportProperties,

    private val calendar: BusinessCalendar
) {
    fun fetch() {
        runner.run(PriceSource.OFFICIAL) {
            val today = calendar.today()
            var from = maxOf(properties.rosstat.startDate, states.fetchedThrough()?.minusWeeks(1) ?: properties.rosstat.startDate)
            var definition: RosstatReportDefinition? = null

            while (from <= today) {
                val until = minOf(from.withMonth(12).withDayOfMonth(31), today)
                val window = FetchWindow(from, until)

                log.info(
                    "Loading data [source={}, provider={}, from={}, until={}]",
                    PriceSource.OFFICIAL, SourceProvider.ROSSTAT, from, until
                )

                val (provider, prices) =
                    try {
                        val prepared = definition ?: rosstat.prepare().also { definition = it }

                        SourceProvider.ROSSTAT to rosstat.fetch(window, prepared)
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()

                        throw error
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
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OfficialImportService::class.java)
    }
}
