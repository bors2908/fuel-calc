package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.regions.model.Region
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.jpa.PriceImportStore
import ge.becrin.fuelcalc.sources.jpa.RegionImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import java.time.LocalDate

class ArchiveFixture(
    private val store: PriceImportStore,

    private val regionStore: RegionImportStore
) {
    fun regions() {
        regionStore.synchronizeRegions(
            listOf(
                Region("A", "Регион А"),
                Region("B", "Регион Б"),
                Region("C", "Регион В"),
                Region("D", "Регион Г")
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
