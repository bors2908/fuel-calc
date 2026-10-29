package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.benzup.BenzupImportService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class ImportScheduler(
    private val regional: RegionalImportService,

    private val benzup: BenzupImportService,

    private val catalogService: RegionCatalogService,

    private val catalog: RegionCatalog,

    private val scheduler: TaskScheduler,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    private val rosstatLock = ReentrantLock()

    private var lastCatalogAttempt: Instant? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!properties.enabled) {
            return
        }

        regional.initialize()
        benzup.initialize()
        catalogService.restore()

        val rosstatAtStartup = properties.rosstat.enabled && properties.rosstat.fetchOnStartup
        val rustataAtStartup = properties.rustata.enabled && properties.rustata.fetchOnStartup
        val benzupAtStartup = properties.benzup.enabled && properties.benzup.fetchOnStartup

        if (!properties.fetchOnStartup || !rosstatAtStartup && !rustataAtStartup && !benzupAtStartup) {
            return
        }

        scheduler.schedule({
            safely {
                if (rosstatAtStartup) {
                    fetchRosstat(true)
                }

                if ((rustataAtStartup || benzupAtStartup) && catalogAvailable("DEPENDENT_SOURCES")) {
                    if (rustataAtStartup) {
                        scheduler.schedule({ safely { fetchRustata(true) } }, clock.instant())
                    }

                    if (benzupAtStartup) {
                        scheduler.schedule({ benzup() }, clock.instant())
                    }
                }
            }
        }, clock.instant())
    }

    @Scheduled(
        cron = "\${imports.rosstat.cron:0 0 */6 * * *}",
        zone = "\${imports.time-zone:Europe/Moscow}"
    )
    fun rosstat() {
        safely { fetchRosstat() }
    }

    @Scheduled(
        cron = "\${imports.rustata.cron:0 30 */6 * * *}",
        zone = "\${imports.time-zone:Europe/Moscow}"
    )
    fun rustata() {
        safely { fetchRustata() }
    }

    @Scheduled(
        cron = "\${imports.benzup.cron:0 15 0,6,12,18 * * *}",
        zone = "\${imports.time-zone:Europe/Moscow}"
    )
    fun benzup() {
        safely {
            if (properties.benzup.enabled && catalogAvailable("BENZUP")) {
                benzup.fetch()
            }
        }
    }

    private fun fetchRosstat(forceMonthly: Boolean = false) {
        if (!properties.rosstat.enabled) {
            return
        }

        rosstatLock.withLock {
            val now = clock.instant()

            if (forceMonthly || !catalog.isReady ||
                lastCatalogAttempt?.plus(properties.rosstat.monthlyInterval)?.isAfter(now) != true
            ) {
                lastCatalogAttempt = now

                catalogService.refresh()
            }

            if (catalogAvailable("ROSSTAT")) {
                regional.fetch("ROSSTAT", forceMonthly)
            }
        }
    }

    private fun fetchRustata(forceMonthly: Boolean = false) {
        if (properties.rustata.enabled && catalogAvailable("RUSTATA")) {
            regional.fetch("RUSTATA", forceMonthly)
        }
    }

    private fun catalogAvailable(provider: String): Boolean {
        val ready = rosstatLock.withLock { catalog.isReady }

        if (!ready) {
            log.warn("Source import postponed [provider={}, reason=REGION_CATALOG_UNAVAILABLE]", provider)
        }

        return ready
    }

    private fun safely(action: () -> Unit) {
        if (properties.enabled) {
            try {
                action()
            } catch (error: Exception) {
                log.error("Scheduled import failed [errorType={}, message={}]", error.javaClass.simpleName, error.message, error)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImportScheduler::class.java)
    }
}
