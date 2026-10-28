package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.service.RegionalPricePublicationService
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.wherefuel.RegionalStationSampler
import java.time.Instant
import java.time.LocalDate

class ArchiveFixture(
    private val store: ImportStore,

    private val publisher: RegionalPricePublicationService
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
        val policy = if (provider == "WHEREFUEL") RegionalStationSampler.POLICY else null
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
        val sampled = provider == "WHEREFUEL"
        val fact = RegionalPriceFact(
            region,
            "DIESEL",
            value.toBigDecimal(),
            publicationDate,
            if (sampled) 5 else null,
            if (sampled) 3 else null,
            if (sampled) reference.lengthOfMonth().takeIf { frequency == PriceFrequency.MONTHLY } ?: 7 else null,
            if (sampled) Instant.parse("2026-10-26T09:00:00Z") else null,
            if (sampled) "fixture-input" else null
        )
        val period =
            if (frequency == PriceFrequency.WEEKLY) PricePeriod(frequency, reference, reference.minusDays(6), reference)
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

        return checkNotNull(
            store.jdbc.queryForObject(
                "SELECT k.current_observation_id FROM regional_price_keys k " +
                "INNER JOIN regions r ON r.id = k.region_id INNER JOIN price_periods p ON p.id = k.period_id " +
                "WHERE r.code = :region AND p.reference_date = :date AND p.frequency = :frequency AND k.provider_code = :provider",
                mapOf("region" to region, "date" to reference, "frequency" to frequency.name, "provider" to provider),
                Long::class.java
            )
        )
    }
}
