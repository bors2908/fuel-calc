package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyRepository
import ge.becrin.fuelcalc.prices.service.RegionalPricePublicationService
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.benzup.BenzupArchiveService
import java.time.Instant
import java.time.LocalDate

class ArchiveFixture(
    private val store: ImportStore,

    private val publisher: RegionalPricePublicationService,

    private val keys: RegionalPriceKeyRepository
) {
    fun regions() {
        store.synchronizeRegions(
            listOf(
                RegionCatalog.Region("A", "Регион А"),
                RegionCatalog.Region("B", "Регион Б"),
                RegionCatalog.Region("C", "Регион В"),
                RegionCatalog.Region("D", "Регион Г")
            )
        )
    }

    fun price(
        region: String,
        value: String,
        date: String = "2026-10-19",
        provider: String = "ROSSTAT",
        frequency: PriceFrequency = PriceFrequency.WEEKLY,
        published: String = "2026-10-21"
    ): Long {
        val reference = LocalDate.parse(date)
        val publicationDate = LocalDate.parse(published)
        val policy = if (provider == "BENZUP") BenzupArchiveService.POLICY else null
        val feed = FeedDefinition(
            "${provider}_${frequency}_API",
            provider,
            if (provider == "RUSTATA") "ROSSTAT" else provider,
            frequency,
            "fixture-$provider-$frequency",
            policy
        )
        store.initialize(listOf(feed))

        val run = checkNotNull(store.begin(feed))
        val sampled = provider == "BENZUP"
        val sampleDays = if (frequency == PriceFrequency.DAILY) 1 else 3
        val expectedDays =
            when (frequency) {
                PriceFrequency.DAILY -> 1
                PriceFrequency.MONTHLY -> reference.lengthOfMonth()
                PriceFrequency.WEEKLY -> 7
            }
        val fact = RegionalPriceFact(
            region,
            "DIESEL",
            value.toBigDecimal(),
            publicationDate,
            if (sampled) 5 else null,
            if (sampled) sampleDays else null,
            if (sampled) expectedDays else null,
            if (sampled) Instant.parse("2026-10-26T09:00:00Z") else null,
            if (sampled) "fixture-input" else null
        )
        val period =
            if (frequency == PriceFrequency.DAILY) PricePeriod(frequency, reference, reference, reference)
            else if (frequency == PriceFrequency.WEEKLY) PricePeriod(frequency, reference, reference.minusDays(6), reference)
            else PricePeriod(frequency, reference, reference.withDayOfMonth(1), reference)

        publisher.publish(
            feed,
            run,
            PreparedPublication(
                period,
                "fixture:$frequency:$date",
                "https://example.test/prices",
                "Test data, not source observations",
                listOf(fact)
            )
        )

        return checkNotNull(keys.currentObservationId(region, reference, frequency, provider))
    }
}
