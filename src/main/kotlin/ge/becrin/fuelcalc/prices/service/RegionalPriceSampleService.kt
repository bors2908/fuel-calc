package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalDailySample
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.wherefuel.RegionalStationSampler
import ge.becrin.fuelcalc.sources.service.ImportHash
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class RegionalPriceSampleService(
    private val store: ImportStore
) {
    @Transactional
    fun append(
        runId: Long,
        samples: List<RegionalDailySample>
    ) {
        if (samples.isEmpty()) {
            return
        }

        store.lockArchive()
        val regions = store.jdbc.queryForList(
            "SELECT id, code FROM regions WHERE code IN (:codes)", mapOf("codes" to samples.map { it.regionCode }.distinct())
        ).associate { it["code"].toString() to it.getValue("id") }
        val existing = store.jdbc.queryForList(
            """
            SELECT DISTINCT ON (region_id, fuel_type_code, sample_date) region_id, fuel_type_code, sample_date, content_hash
            FROM regional_price_samples
            WHERE region_id IN (:regions) AND provider_code = 'WHEREFUEL' AND sampling_policy_code = :policy
                AND sample_date IN (:dates)
            ORDER BY region_id, fuel_type_code, sample_date, collected_at DESC, id DESC
            """.trimIndent(),
            mapOf(
                "regions" to regions.values, "policy" to RegionalStationSampler.POLICY,
                "dates" to samples.map { it.collectedAt.atZone(ZONE).toLocalDate() }.distinct()
            )
        ).associate { Triple(it["region_id"], it["fuel_type_code"], it["sample_date"].toString()) to it["content_hash"] }
        val rows = samples.mapNotNull { sample ->
            val region = regions.getValue(sample.regionCode)
            val date = sample.collectedAt.atZone(ZONE).toLocalDate()

            if (existing[Triple(region, sample.fuelCode, date.toString())] == sample.contentHash) {
                null
            } else {
                mapOf<String, Any?>(
                    "region" to region, "fuel" to sample.fuelCode, "date" to date, "policy" to RegionalStationSampler.POLICY,
                    "price" to sample.price, "stations" to sample.stationCount, "collected" to Timestamp.from(sample.collectedAt),
                    "oldest" to Timestamp.from(sample.oldestPriceAt), "newest" to Timestamp.from(sample.newestPriceAt),
                    "run" to runId, "hash" to sample.contentHash
                )
            }
        }

        store.batch(
            """
            INSERT INTO regional_price_samples (
                provider_code, region_id, fuel_type_code, sample_date, sampling_policy_code, price_rub_per_liter,
                station_count, collected_at, oldest_price_at, newest_price_at, first_import_run_id, content_hash
            ) VALUES ('WHEREFUEL', :region, :fuel, :date, :policy, :price, :stations, :collected, :oldest, :newest, :run, :hash)
            """.trimIndent(),
            rows
        )
    }

    fun prepare(
        frequency: PriceFrequency,
        today: LocalDate,
        url: String
    ): List<PreparedPublication> {
        val periods = store.jdbc.queryForList(
            """
            SELECT frequency, reference_date, start_date, end_date FROM price_periods
            WHERE frequency = :frequency AND reference_date <= :today AND end_date < :today AND start_date IS NOT NULL
                AND end_date >= (SELECT MIN(sample_date) FROM regional_price_samples WHERE sampling_policy_code = :policy)
            ORDER BY reference_date
            """.trimIndent(),
            mapOf("frequency" to frequency.name, "today" to today, "policy" to RegionalStationSampler.POLICY)
        ).map { row ->
            PricePeriod(
                frequency, LocalDate.parse(row["reference_date"].toString()),
                LocalDate.parse(row["start_date"].toString()), LocalDate.parse(row["end_date"].toString())
            )
        }.toMutableList()

        if (frequency == PriceFrequency.MONTHLY) {
            val first = store.jdbc.queryForObject(
                "SELECT MIN(sample_date) FROM regional_price_samples WHERE sampling_policy_code = :policy",
                mapOf("policy" to RegionalStationSampler.POLICY), LocalDate::class.java
            )
            var month = first?.withDayOfMonth(1)

            while (month != null && month.plusMonths(1) <= today) {
                val end = month.plusMonths(1).minusDays(1)
                val candidate = PricePeriod(frequency, end, month, end)

                if (periods.none { it.referenceDate == end }) {
                    periods.add(candidate)
                }

                month = month.plusMonths(1)
            }
        }

        if (periods.isEmpty()) {
            return emptyList()
        }

        val daily = store.jdbc.queryForList(
            """
            SELECT DISTINCT ON (s.region_id, s.fuel_type_code, s.sample_date)
                r.code, s.fuel_type_code, s.sample_date, s.price_rub_per_liter, s.station_count, s.collected_at, s.content_hash, s.id
            FROM regional_price_samples s
            INNER JOIN regions r ON r.id = s.region_id
            WHERE s.provider_code = 'WHEREFUEL' AND s.sampling_policy_code = :policy
                AND s.sample_date BETWEEN :start AND :end
            ORDER BY s.region_id, s.fuel_type_code, s.sample_date, s.collected_at DESC, s.id DESC
            """.trimIndent(),
            mapOf(
                "policy" to RegionalStationSampler.POLICY,
                "start" to periods.minOf { checkNotNull(it.startDate) }, "end" to periods.maxOf { checkNotNull(it.endDate) }
            )
        )

        return periods.sortedBy { it.referenceDate }.mapNotNull { period ->
            val start = checkNotNull(period.startDate)
            val end = checkNotNull(period.endDate)
            val expected = ChronoUnit.DAYS.between(start, end).toInt() + 1
            val facts = daily.filter { LocalDate.parse(it["sample_date"].toString()) in start..end }
                .groupBy { it["code"].toString() to it["fuel_type_code"].toString() }.map { (cell, days) ->
                    val mean = days.fold(BigDecimal.ZERO) { sum, day -> sum + (day["price_rub_per_liter"] as BigDecimal) }
                        .divide(days.size.toBigDecimal(), 6, RoundingMode.HALF_UP)

                    RegionalPriceFact(
                        cell.first, cell.second, mean, null,
                        days.minOf { (it["station_count"] as Number).toInt() }, days.size, expected,
                        days.maxOf { (it["collected_at"] as Timestamp).toInstant() },
                        ImportHash.hash(days.sortedBy { it["sample_date"].toString() }.flatMap {
                            listOf(it["sample_date"], it["content_hash"], it["collected_at"], it["id"])
                        })
                    )
                }

            if (facts.isEmpty()) {
                null
            } else {
                PreparedPublication(
                    period, "${RegionalStationSampler.POLICY}:${period.key}", url,
                    "ГдеТопливо · средняя по выборке АЗС, ₽/л · ${RegionalStationSampler.POLICY}", facts
                )
            }
        }
    }

    companion object {
        private val ZONE = ZoneId.of("Europe/Moscow")
    }
}
