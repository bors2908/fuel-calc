package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.prices.dto.RegionalPriceSampleDto
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.model.SeriesBounds
import ge.becrin.fuelcalc.prices.service.SourceFallbackPolicy
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceAggregationMethod
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.dto.SourceRefDto
import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Repository
class PriceQueryExecutor(
    private val jdbc: NamedParameterJdbcTemplate,

    private val policy: SourceFallbackPolicy
) {
    fun range(selection: PriceSelection): List<PriceRow> =
        rows(selection, "", "ORDER BY x.reference_date, x.period_key, x.region_code")

    fun boundaries(selection: PriceSelection): List<PriceRow> {
        val cte = cte(true)
        val sql = cte + """
            , boundary_rows AS (
                SELECT resolved.*,
                    ROW_NUMBER() OVER (PARTITION BY region_code ORDER BY reference_date, period_id) AS first_n,
                    ROW_NUMBER() OVER (PARTITION BY region_code ORDER BY reference_date DESC, period_id DESC) AS last_n
                FROM resolved
            )
            SELECT $DETAIL_COLUMNS
            FROM boundary_rows x
            $DETAIL_JOINS
            WHERE x.first_n = 1 OR x.last_n = 1
            ORDER BY x.reference_date, x.region_code
        """.trimIndent()

        return jdbc.query(sql, parameters(selection), ::row)
    }

    fun commonPeriod(selection: PriceSelection): Long? {
        val sql = cte(true) + """
            SELECT period_id
            FROM resolved
            GROUP BY period_id, reference_date
            HAVING COUNT(DISTINCT region_code) = :regionCount
            ORDER BY reference_date DESC, period_id DESC
            LIMIT 1
        """.trimIndent()

        return jdbc.queryForList(sql, parameters(selection), Long::class.java).singleOrNull()
    }

    fun atPeriod(
        selection: PriceSelection,
        periodId: Long
    ): List<PriceRow> =
        rows(selection, "WHERE x.period_id = :periodId", "ORDER BY x.region_code", mapOf("periodId" to periodId))

    fun bounds(
        regions: List<String>,
        fuels: List<FuelType>,
        frequencies: List<PriceFrequency>,
        preferred: PriceSource
    ): List<SeriesBounds> {
        val sql = cte(false) + """
            SELECT x.region_code, x.fuel_type_code, x.frequency,
                MIN(x.reference_date) AS first_date, MAX(x.reference_date) AS last_date, COUNT(*) AS period_count,
                (ARRAY_AGG(x.provider_code ORDER BY x.reference_date DESC))[1] AS latest_provider,
                (ARRAY_AGG(pub.feed_id ORDER BY x.reference_date DESC))[1] AS latest_feed_id
            FROM resolved x
            INNER JOIN source_publications pub ON pub.id = x.publication_id
            GROUP BY x.region_code, x.fuel_type_code, x.frequency
        """.trimIndent()

        return jdbc.query(sql, parameters(regions, fuels, frequencies, preferred)) { r, _ ->
            SeriesBounds(
                r.getString("region_code"),
                FuelType.valueOf(r.getString("fuel_type_code")),
                PriceFrequency.valueOf(r.getString("frequency")),
                r.getDate("first_date").toLocalDate(),
                r.getDate("last_date").toLocalDate(),
                r.getInt("period_count"),
                PriceSource.valueOf(r.getString("latest_provider")),
                r.getLong("latest_feed_id")
            )
        }
    }

    fun details(id: Long): PriceRow? {
        val sql = """
            WITH observation AS (
                SELECT $CANDIDATE_COLUMNS
                FROM regional_price_keys k
                INNER JOIN regional_price_observations o ON o.price_key_id = k.id
                INNER JOIN price_periods p ON p.id = k.period_id
                INNER JOIN regions r ON r.id = k.region_id
                WHERE o.id = :id
            )
            SELECT $DETAIL_COLUMNS
            FROM observation x
            $DETAIL_JOINS
        """.trimIndent()

        return jdbc.query(sql, mapOf("id" to id), ::row).singleOrNull()
    }

    fun latestForProvider(row: PriceRow): LocalDate? =
        jdbc.queryForObject(
            """
            SELECT MAX(p.reference_date)
            FROM regional_price_keys k
            INNER JOIN regions r ON r.id = k.region_id
            INNER JOIN price_periods p ON p.id = k.period_id
            WHERE r.code = :region AND k.fuel_type_code = :fuel AND k.provider_code = :provider
                AND p.frequency = :frequency AND p.basis = 'REGIONAL_CONSUMER_AVERAGE'
                AND k.current_observation_id IS NOT NULL
            """.trimIndent(),
            mapOf(
                "region" to row.region.code,
                "fuel" to row.fuel.code.name,
                "provider" to row.source.provider.code,
                "frequency" to row.period.frequency.name
            ),
            LocalDate::class.java
        )

    private fun rows(
        selection: PriceSelection,
        where: String,
        order: String,
        extra: Map<String, Any> = emptyMap()
    ): List<PriceRow> =
        jdbc.query(
            cte(true) + "SELECT $DETAIL_COLUMNS FROM resolved x $DETAIL_JOINS $where $order",
            parameters(selection) + extra,
            ::row
        )

    private fun parameters(selection: PriceSelection): Map<String, Any> =
        parameters(selection.regions.map { it.code }, listOf(selection.fuel.code), listOf(selection.frequency), selection.preferred) +
            mapOf("from" to selection.from, "until" to selection.until, "regionCount" to selection.regions.size)

    private fun parameters(
        regions: List<String>,
        fuels: List<FuelType>,
        frequencies: List<PriceFrequency>,
        preferred: PriceSource
    ): Map<String, Any> {
        val order = policy.order(preferred)

        return mapOf(
            "regions" to regions,
            "fuels" to fuels.map { it.name },
            "frequencies" to frequencies.map { it.name },
            "firstProvider" to order[0].name,
            "secondProvider" to order[1].name
        )
    }

    private fun cte(inRange: Boolean): String =
        """
        WITH ranked AS (
            SELECT $CANDIDATE_COLUMNS,
                ROW_NUMBER() OVER (
                    PARTITION BY k.region_id, k.fuel_type_code, k.period_id
                    ORDER BY CASE k.provider_code WHEN :firstProvider THEN 0 WHEN :secondProvider THEN 1 ELSE 2 END
                ) AS source_rank
            FROM regional_price_keys k
            INNER JOIN regional_price_observations o ON o.id = k.current_observation_id AND o.price_key_id = k.id
            INNER JOIN price_periods p ON p.id = k.period_id
            INNER JOIN regions r ON r.id = k.region_id
            WHERE r.code IN (:regions) AND k.fuel_type_code IN (:fuels) AND p.frequency IN (:frequencies)
                AND p.basis = 'REGIONAL_CONSUMER_AVERAGE'
                ${if (inRange) "AND p.reference_date BETWEEN :from AND :until" else ""}
        ), resolved AS (SELECT * FROM ranked WHERE source_rank = 1)
        """.trimIndent() + "\n"

    private fun row(
        r: ResultSet,
        index: Int
    ): PriceRow {
        val method = PriceAggregationMethod.valueOf(r.getString("aggregation_method"))
        val sample =
            if (method == PriceAggregationMethod.STATION_SAMPLE_MEAN) {
                RegionalPriceSampleDto(
                    checkNotNull(r.getString("sampling_policy_code")),
                    r.getInt("sample_min_station_count"),
                    r.getInt("sample_day_count"),
                    r.getInt("sample_expected_day_count"),
                    checkNotNull(r.getTimestamp("sample_collected_at")).toInstant()
                )
            } else {
                null
            }
        val source = SourceSummaryDto(
            SourceRefDto(r.getString("publisher_code"), r.getString("publisher_name"), r.getString("publisher_url")),
            SourceRefDto(r.getString("provider_code"), r.getString("provider_name"), r.getString("provider_url")),
            r.getString("external_code"),
            r.getString("external_publication_id"),
            method,
            sample
        )

        return PriceRow(
            r.getLong("observation_id"),
            RegionRefDto(r.getString("region_code"), r.getString("region_name")),
            FuelTypeRefDto(FuelType.valueOf(r.getString("fuel_type_code")), r.getString("fuel_name")),
            period(r),
            r.getBigDecimal("price_rub_per_liter"),
            r.getLong("feed_id"),
            source,
            r.getDate("publication_date")?.toLocalDate(),
            r.getTimestamp("published_at")?.toInstant(),
            r.getTimestamp("loaded_at").toInstant(),
            r.getString("publication_url"),
            r.getString("attribution"),
            r.getString("methodology_url"),
            r.getString("note")
        )
    }

    private fun period(row: ResultSet): PricePeriodDto {
        val frequency = PriceFrequency.valueOf(row.getString("frequency"))
        val date = row.getDate("reference_date").toLocalDate()
        val start = row.getDate("start_date")?.toLocalDate()
        val end = row.getDate("end_date")?.toLocalDate()
        val label =
            when {
                frequency == PriceFrequency.MONTHLY -> date.format(MONTH_LABEL).replaceFirstChar { it.titlecase(Locale.of("ru")) }
                start != null && end != null -> "${start.format(DATE_LABEL)} — ${end.format(DATE_LABEL)}"
                else -> "Неделя на ${date.format(DATE_LABEL)}"
            }

        return PricePeriodDto(row.getString("period_key"), frequency, date, label, start, end)
    }

    companion object {
        private val MONTH_LABEL = DateTimeFormatter.ofPattern("LLLL uuuu", Locale.of("ru"))

        private val DATE_LABEL = DateTimeFormatter.ofPattern("dd.MM.uuuu")

        private const val CANDIDATE_COLUMNS = """
            r.code AS region_code, r.name AS region_name, k.fuel_type_code, k.provider_code,
            p.id AS period_id, p.period_key, p.frequency, p.reference_date, p.label, p.start_date, p.end_date,
            o.id AS observation_id, o.price_rub_per_liter, o.publication_id,
            o.sample_min_station_count, o.sample_day_count, o.sample_expected_day_count, o.sample_collected_at
        """

        private const val DETAIL_COLUMNS = """
            x.*, ft.name AS fuel_name, pub.feed_id, pub.external_publication_id, pub.publication_date,
            pub.published_at, pub.loaded_at, pub.publication_url, pub.attribution, pub.methodology_url, pub.note,
            f.external_code, f.aggregation_method, f.sampling_policy_code, f.publisher_code,
            publisher.name AS publisher_name, publisher.url AS publisher_url,
            provider.name AS provider_name, provider.url AS provider_url
        """

        private const val DETAIL_JOINS = """
            INNER JOIN fuel_types ft ON ft.code = x.fuel_type_code
            INNER JOIN source_publications pub ON pub.id = x.publication_id
            INNER JOIN source_feeds f ON f.id = pub.feed_id
            INNER JOIN data_sources publisher ON publisher.code = f.publisher_code
            INNER JOIN data_sources provider ON provider.code = x.provider_code
        """
    }
}
