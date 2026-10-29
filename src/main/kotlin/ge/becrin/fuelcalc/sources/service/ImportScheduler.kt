package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.benzup.BenzupImportService
import ge.becrin.fuelcalc.sources.config.ImportProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ImportScheduler(
    private val official: OfficialImportService,

    private val market: BenzupImportService,

    private val regions: RegionCatalogService,

    private val scheduler: TaskScheduler,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!properties.enabled) {
            return
        }

        regions.restore()
        scheduler.schedule({
            safely {
                regions.refresh()

                if (properties.fetchOnStartup) {
                    scheduler.schedule({ official() }, clock.instant())
                    scheduler.schedule({ market() }, clock.instant())
                }
            }
        }, clock.instant())
    }

    @Scheduled(cron = "\${imports.regions.cron:0 0 3 * * MON}", zone = "\${imports.time-zone:Europe/Moscow}")
    fun regions() {
        safely { regions.refresh() }
    }

    @Scheduled(cron = "\${imports.rosstat.cron:0 0 0 * * *}", zone = "\${imports.time-zone:Europe/Moscow}")
    fun official() {
        safely { official.fetch() }
    }

    @Scheduled(cron = "\${imports.benzup.cron:0 0 12 * * *}", zone = "\${imports.time-zone:Europe/Moscow}")
    fun market() {
        safely { market.fetch() }
    }

    private fun safely(action: () -> Unit) {
        if (properties.enabled) {
            try {
                action()
            } catch (error: Exception) {
                log.error("Scheduled import failed", error)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImportScheduler::class.java)
    }
}
