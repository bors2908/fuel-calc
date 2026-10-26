package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Repository
class ImportStore(
    val jdbc: NamedParameterJdbcTemplate,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    fun batch(
        sql: String,
        rows: List<Map<String, Any?>>
    ) {
        for (chunk in rows.chunked(properties.batchSize)) {
            jdbc.batchUpdate(sql, chunk.map { MapSqlParameterSource(it) }.toTypedArray())
        }
    }

    @Transactional
    fun initialize(feeds: List<FeedDefinition>) {
        batch(
            """
            INSERT INTO source_feeds (
                code, publisher_code, provider_code, external_code, frequency, aggregation_method,
                sampling_policy_code, enabled, update_status
            ) VALUES (:code, :publisher, :provider, :external, :frequency, :method, :policy, TRUE, 'UNKNOWN')
            ON CONFLICT (code) DO NOTHING
            """.trimIndent(),
            feeds.map { feed ->
                mapOf(
                    "code" to feed.code, "publisher" to feed.publisher, "provider" to feed.provider,
                    "external" to feed.externalCode, "frequency" to feed.frequency.name,
                    "method" to if (feed.samplingPolicy == null) "OFFICIAL_STATISTICS" else "STATION_SAMPLE_MEAN",
                    "policy" to feed.samplingPolicy
                )
            }
        )
        jdbc.update(
            """
            INSERT INTO source_fetch_states (feed_id, updated_at)
            SELECT id, :now FROM source_feeds
            ON CONFLICT (feed_id) DO NOTHING
            """.trimIndent(),
            mapOf("now" to now())
        )
        jdbc.update(
            """
            UPDATE import_runs
            SET status = 'FAILED', finished_at = :now, error_code = 'INTERRUPTED', error_message = 'Application restarted'
            WHERE status = 'RUNNING'
            """.trimIndent(),
            mapOf("now" to now())
        )
    }

    fun savedRegions(): List<RegionCatalog.Region> =
        jdbc.query("SELECT code, name FROM regions ORDER BY code", emptyMap<String, Any>()) { row, _ ->
            RegionCatalog.Region(row.getString("code"), row.getString("name"))
        }

    @Transactional
    fun synchronizeRegions(regions: List<RegionCatalog.Region>) {
        require(regions.isNotEmpty()) { "Region catalog must not be empty" }

        lockArchive()

        var changed = false
        val timestamp = now()

        for (chunk in regions.chunked(properties.batchSize)) {
            val counts = jdbc.batchUpdate(
                """
                INSERT INTO regions (code, name, created_at)
                VALUES (:code, :name, :now)
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name
                WHERE regions.name IS DISTINCT FROM EXCLUDED.name
                """.trimIndent(),
                chunk.map { MapSqlParameterSource(mapOf("code" to it.code, "name" to it.name, "now" to timestamp)) }.toTypedArray()
            )

            changed = changed || counts.any { it != 0 }
        }

        if (changed) {
            bumpRevision()
        }
    }

    @Transactional
    fun begin(feed: FeedDefinition): Long? {
        val rows = jdbc.queryForList(
            """
            SELECT f.id FROM source_feeds f
            INNER JOIN source_fetch_states s ON s.feed_id = f.id
            WHERE f.code = :code AND f.enabled
                AND (s.next_fetch_not_before IS NULL OR s.next_fetch_not_before <= :now)
            """.trimIndent(),
            mapOf("code" to feed.code, "now" to now())
        )
        val id = (rows.singleOrNull()?.get("id") as? Number)?.toLong() ?: return null

        return jdbc.queryForObject(
            "INSERT INTO import_runs (feed_id, started_at, status, imported_count, rejected_count) " +
                "VALUES (:id, :now, 'RUNNING', 0, 0) RETURNING id",
            mapOf("id" to id, "now" to now()),
            Long::class.java
        )
    }

    @Transactional
    fun finish(
        runId: Long,
        count: Int,
        error: SourceImportException?,
        freshness: String
    ) {
        lockArchive()
        val args = mapOf(
            "id" to runId, "now" to now(), "count" to count, "status" to if (error == null) "SUCCEEDED" else "FAILED",
            "code" to error?.code, "message" to error?.message, "freshness" to if (error == null) freshness else "UNAVAILABLE",
            "retry" to error?.retryAt?.let(Timestamp::from)
        )

        jdbc.update(
            """
            UPDATE import_runs
            SET status = :status, finished_at = :now, imported_count = :count, error_code = :code, error_message = :message
            WHERE id = :id AND status = 'RUNNING'
            """.trimIndent(),
            args
        )
        jdbc.update(
            """
            UPDATE source_feeds
            SET update_status = :freshness, update_message = :message,
                last_success_at = CASE WHEN :status = 'SUCCEEDED' THEN :now ELSE last_success_at END
            WHERE id = (SELECT feed_id FROM import_runs WHERE id = :id)
            """.trimIndent(),
            args
        )

        if (error?.retryAt != null) {
            jdbc.update(
                """
                UPDATE source_fetch_states SET next_fetch_not_before = :retry, updated_at = :now
                WHERE feed_id IN (
                    SELECT id FROM source_feeds WHERE provider_code = (
                        SELECT f.provider_code FROM source_feeds f
                        INNER JOIN import_runs r ON r.feed_id = f.id WHERE r.id = :id
                    )
                )
                """.trimIndent(),
                args
            )
        }

        bumpRevision()
    }

    fun state(feed: FeedDefinition): Map<String, Any?> =
        jdbc.queryForMap(
            "SELECT s.* FROM source_fetch_states s INNER JOIN source_feeds f ON f.id = s.feed_id WHERE f.code = :code",
            mapOf("code" to feed.code)
        )

    @Transactional
    fun saveProgress(
        feed: FeedDefinition,
        mode: String,
        next: LocalDate?,
        until: LocalDate?
    ) {
        val args = mapOf("code" to feed.code, "next" to next, "until" to until, "now" to now())
        val sql =
            if (mode == "BACKFILL") {
                """
                UPDATE source_fetch_states
                SET backfill_next_from = :next, backfill_until = :until, updated_at = :now,
                    backfill_completed_at = CASE WHEN CAST(:next AS date) IS NULL THEN CAST(:now AS timestamptz) ELSE NULL END
                WHERE feed_id = (SELECT id FROM source_feeds WHERE code = :code)
                """.trimIndent()
            } else {
                """
                UPDATE source_fetch_states
                SET reconciliation_next_from = :next, reconciliation_until = :until, updated_at = :now,
                    last_reconciled_at = CASE WHEN CAST(:next AS date) IS NULL THEN :now ELSE last_reconciled_at END
                WHERE feed_id = (SELECT id FROM source_feeds WHERE code = :code)
                """.trimIndent()
            }

        jdbc.update(sql, args)
    }

    fun lockArchive() {
        jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1 FOR UPDATE", emptyMap<String, Any>(), Long::class.java)
    }

    fun bumpRevision() {
        jdbc.update("UPDATE archive_state SET revision = revision + 1, updated_at = :now WHERE id = 1", mapOf("now" to now()))
    }

    fun now(): Timestamp =
        Timestamp.from(clock.instant())
}
