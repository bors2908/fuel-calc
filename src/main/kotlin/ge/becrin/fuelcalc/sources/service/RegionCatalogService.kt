package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.RegionImportStore
import ge.becrin.fuelcalc.sources.rosstat.RosstatRegionParser
import java.net.URI
import java.util.concurrent.locks.ReentrantLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RegionCatalogService(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val store: RegionImportStore,

    private val catalog: RegionCatalog,

    private val parser: RosstatRegionParser
) {
    private val lock = ReentrantLock()

    fun restore() {
        val saved = store.savedRegions()

        if (saved.isNotEmpty()) {
            catalog.replace(saved)
        }
    }

    fun refresh(): Boolean {
        if (!lock.tryLock()) {
            return catalog.isReady
        }

        try {
            http.run {
                val full = http.json(URI.create(properties.regions.okatoEndpoint))
                val selection = http.json(URI.create(properties.regions.selectionEndpoint))

                val selected = parser.parse(full, selection)

                val hash = ImportHash.hash(selected.flatMap { listOf(it.okatoCode, it.name) })
                val changed = store.synchronizeRegions(selected, hash)

                restore()

                log.info("Region catalog checked [changed={}, territories={}]", changed, selected.size)
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()

            throw error
        } catch (error: Exception) {
            log.warn("Region catalog unavailable; saved catalog retained", error)
        } finally {
            lock.unlock()
        }

        return catalog.isReady
    }

    companion object {
        private val log = LoggerFactory.getLogger(RegionCatalogService::class.java)
    }
}
