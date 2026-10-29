package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock

@Service
class BenzupImportService(
    private val client: BenzupClient,

    private val mapping: BenzupRegionMapping,

    private val catalog: RegionCatalog,

    private val store: ImportStore,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    private val lock = ReentrantLock()

    fun fetch() {
        if (!properties.enabled || !catalog.isReady || !lock.tryLock()) {
            return
        }

        try {
            val today = LocalDate.now(clock.withZone(ZoneId.of(properties.timeZone)))

            log.info(
                "Loading data [source={}, provider={}, from={}, until={}]",
                PriceSource.MARKET, SourceProvider.BENZUP, today, today
            )

            val prices = client.fetch().mapNotNull { row ->
                mapping.okatoByRegionId[row.regionId]?.let(catalog::byOkatoCode)?.let { region ->
                    FetchedPrice(region.okatoCode, FuelType.valueOf(row.fuelCode), today, row.price)
                }
            }

            require(prices.isNotEmpty()) { "BenzUp snapshot has no mapped regions" }

            val changed = store.saveWindow(SourceProvider.BENZUP, FetchWindow(today, today), prices)

            log.info(
                "Data loaded [source={}, provider={}, from={}, until={}, prices={}, changed={}]",
                PriceSource.MARKET, SourceProvider.BENZUP, today, today, prices.size, changed
            )
        } catch (error: Exception) {
            store.failed(PriceSource.MARKET, error)

            log.warn("Data loading failed; saved data retained [source={}]", PriceSource.MARKET, error)
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BenzupImportService::class.java)
    }
}
