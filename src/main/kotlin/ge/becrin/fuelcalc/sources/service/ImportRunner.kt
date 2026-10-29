package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStateStore
import java.util.concurrent.locks.ReentrantLock
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class ImportRunner(
    private val properties: ImportProperties,

    private val catalog: RegionCatalog,

    private val http: SourceHttpClient,

    private val states: ImportStateStore,

    private val events: ApplicationEventPublisher
) {
    private val locks = PriceSource.entries.associateWith { ReentrantLock() }

    fun run(
        source: PriceSource,
        action: () -> Unit
    ) {
        val lock = locks.getValue(source)

        if (!properties.enabled || !catalog.isReady || !lock.tryLock()) {
            return
        }

        try {
            http.run(action)

            events.publishEvent(SourceImportCompleted(source))
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()

            throw error
        } catch (error: Exception) {
            states.failed(source, error)

            log.warn("Data loading failed; saved data retained [source={}]", source, error)
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImportRunner::class.java)
    }
}
