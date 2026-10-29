package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.config.BusinessCalendar
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.PriceImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.ImportRunner
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BenzupImportService(
    private val client: BenzupClient,

    private val mapping: BenzupRegionMapping,

    private val catalog: RegionCatalog,

    private val store: PriceImportStore,

    private val runner: ImportRunner,

    private val calendar: BusinessCalendar,

    private val properties: ImportProperties
) {
    fun fetch() {
        val today = calendar.today()

        if (today < properties.benzup.startDate) {
            return
        }

        runner.run(PriceSource.MARKET) {
            log.info(
                "Loading data [source={}, provider={}, from={}, until={}]",
                PriceSource.MARKET, SourceProvider.BENZUP, today, today
            )

            val prices = client.fetch().mapNotNull { row ->
                mapping.okatoByRegionId[row.regionId]?.let(catalog::byOkatoCode)?.let { region ->
                    FetchedPrice(region.okatoCode, row.fuel, today, row.price)
                }
            }

            require(prices.isNotEmpty()) { "BenzUp snapshot has no mapped regions" }

            val changed = store.saveWindow(SourceProvider.BENZUP, FetchWindow(today, today), prices)

            log.info(
                "Data loaded [source={}, provider={}, from={}, until={}, prices={}, changed={}]",
                PriceSource.MARKET, SourceProvider.BENZUP, today, today, prices.size, changed
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BenzupImportService::class.java)
    }
}
