package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.util.concurrent.locks.ReentrantLock

@Service
class RegionCatalogService(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val store: ImportStore,

    private val catalog: RegionCatalog
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
            http.beginRun()

            val full = http.json(URI.create(properties.regions.okatoEndpoint))
            val selection = http.json(URI.create(properties.regions.selectionEndpoint))

            require(full.isArray && selection.isArray && !selection.isEmpty) { "Invalid OKATO catalog or selection" }

            val byId = full.associateBy { it.path("id").asLong() }
            val ids = selection.toList().map { it.path("-298").asLong() }

            require(byId.size == full.size() && ids.distinct().size == ids.size && ids.all { it in byId }) {
                "Invalid or unresolved OKATO selection references"
            }

            val selected = ids.map { id ->
                val row = byId.getValue(id)

                RegionCatalog.Region(row.path("-17006").asString(""), row.path("-17007").asString(""))
            }.sortedBy { it.okatoCode }

            require(selected.all { it.okatoCode.isNotBlank() && it.name.isNotBlank() }) { "Invalid selected territory" }
            require(selected.map { it.okatoCode }.distinct().size == selected.size) { "Duplicate selected OKATO code" }

            val hash = ImportHash.hash(selected.flatMap { listOf(it.okatoCode, it.name) })
            val changed = store.synchronizeRegions(selected, hash)

            restore()

            log.info("Region catalog checked [changed={}, territories={}]", changed, selected.size)
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
