package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import java.time.LocalDate

class ArchiveFixture(private val store: ImportStore) {
    fun regions() {
        store.synchronizeRegions(
            listOf(
                RegionCatalog.Region("A", "Регион А"),
                RegionCatalog.Region("B", "Регион Б"),
                RegionCatalog.Region("C", "Регион В"),
                RegionCatalog.Region("D", "Регион Г")
            ),
            "test-regions-v1"
        )
    }

    fun price(
        region: String,
        value: String,
        date: String = "2026-10-19",
        provider: SourceProvider = SourceProvider.ROSSTAT
    ) {
        val reference = LocalDate.parse(date)

        store.saveWindow(
            provider,
            FetchWindow(reference, reference),
            listOf(FetchedPrice(region, FuelType.DIESEL, reference, value.toBigDecimal()))
        )
    }
}
