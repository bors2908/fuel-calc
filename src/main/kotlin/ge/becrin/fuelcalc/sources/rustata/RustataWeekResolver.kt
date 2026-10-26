package ge.becrin.fuelcalc.sources.rustata

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class RustataWeekResolver(
    private val store: ImportStore
) {
    fun resolve(
        feed: FeedDefinition,
        window: FetchWindow,
        factsByDate: Map<LocalDate, List<RegionalPriceFact>>
    ): Map<LocalDate, PricePeriod> {
        if (factsByDate.isEmpty()) {
            return emptyMap()
        }

        val periods = store.jdbc.queryForList(
            """
            SELECT p.reference_date, p.start_date, p.end_date
            FROM price_periods p
            WHERE p.frequency = 'WEEKLY' AND p.start_date <= :until AND p.end_date >= :from
                AND EXISTS (
                    SELECT 1 FROM regional_price_keys k
                    WHERE k.period_id = p.id AND k.provider_code = 'ROSSTAT' AND k.current_observation_id IS NOT NULL
                )
            """.trimIndent(),
            mapOf("from" to factsByDate.keys.min(), "until" to factsByDate.keys.max())
        ).map { row ->
            PricePeriod(
                PriceFrequency.WEEKLY, LocalDate.parse(row["reference_date"].toString()),
                LocalDate.parse(row["start_date"].toString()), LocalDate.parse(row["end_date"].toString())
            )
        }
        val calendar = periods.associateBy { it.referenceDate }
        val resolved = factsByDate.keys.associateWith { date ->
            calendar[date] ?: periods.singleOrNull { date in checkNotNull(it.startDate)..checkNotNull(it.endDate) }
        }.toMutableMap()
        val shifted = resolved.filter { (date, period) -> period != null && period.referenceDate != date }

        if (shifted.isNotEmpty()) {
            val official = store.jdbc.queryForList(
                """
                SELECT p.reference_date, r.code AS region_code, k.fuel_type_code, o.price_rub_per_liter AS price
                FROM regional_price_keys k
                INNER JOIN price_periods p ON p.id = k.period_id
                INNER JOIN regions r ON r.id = k.region_id
                INNER JOIN regional_price_observations o ON o.id = k.current_observation_id
                WHERE k.provider_code = 'ROSSTAT' AND p.frequency = 'WEEKLY' AND p.reference_date IN (:dates)
                """.trimIndent(),
                mapOf("dates" to shifted.values.map { checkNotNull(it).referenceDate }.distinct())
            ).groupBy { LocalDate.parse(it["reference_date"].toString()) }

            for ((date, candidate) in shifted) {
                val period = checkNotNull(candidate)
                val prices = official[period.referenceDate].orEmpty().associate {
                    (it["region_code"].toString() to it["fuel_type_code"].toString()) to it["price"] as BigDecimal
                }
                val facts = factsByDate.getValue(date)
                val matched = facts.all { fact -> prices[fact.regionCode to fact.fuelCode]?.compareTo(fact.price) == 0 }

                if (matched) {
                    log.warn(
                        "Rustata weekly label normalized against official prices [sourceDate={}, referenceDate={}, " +
                            "startDate={}, endDate={}, verifiedFacts={}]",
                        date, period.referenceDate, period.startDate, period.endDate, facts.size
                    )
                } else {
                    resolved[date] = null
                }
            }
        }

        val duplicates = resolved.values.filterNotNull().groupingBy { it.referenceDate }.eachCount().filterValues { it > 1 }.keys
        val missing = resolved.filter { (_, period) -> period == null || period.referenceDate in duplicates }.keys.sorted()

        if (missing.isNotEmpty()) {
            throw SourceImportException(
                "UNCONFIRMED_WEEK",
                "Rustata weekly dates have no unambiguous verified Rosstat periods " +
                    "[dataset=${feed.externalCode}, from=${window.from}, until=${window.until}, " +
                    "missingCount=${missing.size}, missingDates=${missing.take(12)}, confirmedCount=${calendar.size}]. " +
                    "Check preceding ROSSTAT_WEEKLY_V1 errors, calendar intervals and official price agreement"
            )
        }

        return resolved.mapValues { checkNotNull(it.value) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RustataWeekResolver::class.java)
    }
}
