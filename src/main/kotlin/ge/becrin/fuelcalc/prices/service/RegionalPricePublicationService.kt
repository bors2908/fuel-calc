package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PublicationResult
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.ImportHash
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDate

@Service
class RegionalPricePublicationService(
    private val store: ImportStore
) {
    @Transactional
    fun publish(
        feed: FeedDefinition,
        runId: Long,
        publication: PreparedPublication
    ): PublicationResult {
        val period = publication.period
        require(period.frequency == feed.frequency)
        val facts = publication.facts.distinct().sortedWith(compareBy({ it.regionCode }, { it.fuelCode }))

        if (facts.isEmpty()) {
            throw SourceImportException("EMPTY_PUBLICATION", "Publication has no regional prices")
        }

        if (facts.groupBy { it.regionCode to it.fuelCode }.any { it.value.size != 1 }) {
            throw SourceImportException("SOURCE_DATA_CONFLICT", "Conflicting values in one publication")
        }

        for (fact in facts) {
            require(fact.price.signum() >= 0 && fact.price.toPlainString().length <= 64)
            require(fact.fuelCode in setOf("AI_92", "AI_95", "DIESEL"))
        }

        store.lockArchive()
        val jdbc = store.jdbc
        val feedId = checkNotNull(jdbc.queryForObject(
            "SELECT f.id FROM source_feeds f INNER JOIN import_runs r ON r.feed_id = f.id " +
                "WHERE r.id = :run AND f.code = :code",
            mapOf("run" to runId, "code" to feed.code), Long::class.java
        ))
        val regions = jdbc.queryForList(
            "SELECT id, code FROM regions WHERE code IN (:codes)", mapOf("codes" to facts.map { it.regionCode })
        )
            .associate { it["code"].toString() to (it["id"] as Number).toLong() }
        require(regions.size == facts.map { it.regionCode }.distinct().size) { "Unmapped region code" }

        jdbc.update(
            """
            INSERT INTO price_periods (period_key, basis, frequency, reference_date, start_date, end_date, label)
            VALUES (:key, 'REGIONAL_CONSUMER_AVERAGE', :frequency, :reference, :start, :end, :label)
            ON CONFLICT (basis, frequency, reference_date) DO NOTHING
            """.trimIndent(),
            mapOf(
                "key" to period.key, "frequency" to period.frequency.name, "reference" to period.referenceDate,
                "start" to period.startDate, "end" to period.endDate, "label" to period.referenceDate.toString()
            )
        )
        val existingPeriod = jdbc.queryForMap(
            "SELECT id, start_date, end_date FROM price_periods WHERE basis = 'REGIONAL_CONSUMER_AVERAGE' " +
                "AND frequency = :frequency AND reference_date = :date",
            mapOf("frequency" to period.frequency.name, "date" to period.referenceDate)
        )
        require(existingPeriod["start_date"]?.toString() == period.startDate?.toString()) { "Period boundary mismatch" }
        require(existingPeriod["end_date"]?.toString() == period.endDate?.toString()) { "Period boundary mismatch" }
        val periodId = (existingPeriod.getValue("id") as Number).toLong()
        val groups = facts.groupBy { it.publicationDate }
        val descriptors = groups.map { (date, rows) ->
            val hash = ImportHash.hash(
                listOf("REGIONAL_V1", feed.code, publication.externalKey, period, publication.url, publication.attribution, date) +
                    rows.flatMap { factFields(it) }
            )

            mapOf<String, Any?>(
                "feed" to feedId, "run" to runId, "key" to publication.externalKey,
                "hash" to hash, "date" to date, "now" to store.now(), "url" to publication.url,
                "attribution" to publication.attribution
            )
        }

        store.batch(
            """
            INSERT INTO source_publications (
                feed_id, first_import_run_id, external_key, content_hash, publication_date, loaded_at, publication_url, attribution
            ) VALUES (:feed, :run, :key, :hash, :date, :now, :url, :attribution)
            ON CONFLICT (feed_id, external_key, content_hash) DO NOTHING
            """.trimIndent(),
            descriptors
        )
        val publicationIds = jdbc.queryForList(
            "SELECT id, content_hash FROM source_publications WHERE feed_id = :feed AND external_key = :key AND content_hash IN (:hashes)",
            mapOf("feed" to feedId, "key" to publication.externalKey, "hashes" to descriptors.map { it.getValue("hash") })
        ).associate { it["content_hash"].toString() to (it["id"] as Number).toLong() }
        val publicationByDate = descriptors.associate { it["date"] to publicationIds.getValue(it["hash"].toString()) }
        val keyRows = facts.map { fact ->
            mapOf<String, Any?>(
                "region" to regions.getValue(fact.regionCode), "fuel" to fact.fuelCode,
                "period" to periodId, "provider" to feed.provider, "now" to store.now()
            )
        }

        store.batch(
            """
            INSERT INTO regional_price_keys (region_id, fuel_type_code, period_id, provider_code, created_at)
            VALUES (:region, :fuel, :period, :provider, :now)
            ON CONFLICT (region_id, fuel_type_code, period_id, provider_code) DO NOTHING
            """.trimIndent(),
            keyRows
        )
        val keys = jdbc.queryForList(
            """
            SELECT k.id, r.code, k.fuel_type_code FROM regional_price_keys k
            INNER JOIN regions r ON r.id = k.region_id
            WHERE k.period_id = :period AND k.provider_code = :provider AND k.region_id IN (:regions)
            """.trimIndent(),
            mapOf("period" to periodId, "provider" to feed.provider, "regions" to regions.values)
        ).associate { (it["code"].toString() to it["fuel_type_code"].toString()) to (it["id"] as Number).toLong() }
        val observationRows = facts.map { fact ->
            mapOf<String, Any?>(
                "key" to keys.getValue(fact.regionCode to fact.fuelCode),
                "publication" to publicationByDate.getValue(fact.publicationDate), "price" to fact.price,
                "stations" to fact.sampleMinStationCount, "days" to fact.sampleDayCount,
                "expected" to fact.sampleExpectedDayCount, "collected" to fact.sampleCollectedAt?.let(Timestamp::from)
            )
        }
        val before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM regional_price_observations WHERE publication_id IN (:ids)",
            mapOf("ids" to publicationIds.values), Int::class.java
        ) ?: 0

        store.batch(
            """
            INSERT INTO regional_price_observations (
                price_key_id, publication_id, price_rub_per_liter, sample_min_station_count,
                sample_day_count, sample_expected_day_count, sample_collected_at
            ) VALUES (:key, :publication, :price, :stations, :days, :expected, :collected)
            ON CONFLICT (publication_id, price_key_id) DO NOTHING
            """.trimIndent(),
            observationRows
        )
        val versions = jdbc.queryForList(
            """
            SELECT o.id, o.price_key_id, o.publication_id, o.price_rub_per_liter, o.sample_collected_at,
                p.publication_date, k.current_observation_id,
                current_o.price_rub_per_liter AS current_price, current_o.sample_collected_at AS current_collected,
                current_p.publication_date AS current_date
            FROM regional_price_observations o
            INNER JOIN source_publications p ON p.id = o.publication_id
            INNER JOIN regional_price_keys k ON k.id = o.price_key_id
            LEFT JOIN regional_price_observations current_o ON current_o.id = k.current_observation_id
            LEFT JOIN source_publications current_p ON current_p.id = current_o.publication_id
            WHERE o.publication_id IN (:ids)
            """.trimIndent(),
            mapOf("ids" to publicationIds.values)
        )
        var conflicts = 0
        val updates = mutableListOf<Map<String, Any?>>()

        for (version in versions) {
            val sameId = version["id"] == version["current_observation_id"]
            val samePrice = (version["current_price"] as? BigDecimal)?.let {
                (version["price_rub_per_liter"] as BigDecimal).compareTo(it) == 0
            } ?: false
            val newer =
                if (feed.samplingPolicy != null) {
                    val current = version["current_collected"] as? Timestamp
                    val candidate = version["sample_collected_at"] as? Timestamp
                    candidate != null && (current == null || candidate.after(current))
                } else {
                    val current = version["current_date"]?.toString()?.let(LocalDate::parse)
                    val candidate = version["publication_date"]?.toString()?.let(LocalDate::parse)
                    candidate != null && current != null && candidate.isAfter(current)
                }

            if (version["current_observation_id"] == null || newer) {
                updates.add(mapOf("id" to version["price_key_id"], "observation" to version["id"]))
            } else if (!sameId && !samePrice) {
                val older = version["publication_date"]?.toString()?.let { candidate ->
                    version["current_date"]?.toString()?.let { current -> candidate < current }
                } ?: false

                if (!older) {
                    conflicts++
                }
            }
        }

        store.batch("UPDATE regional_price_keys SET current_observation_id = :observation WHERE id = :id", updates)
        val inserted = versions.size - before

        if (inserted > 0 || updates.isNotEmpty()) {
            store.bumpRevision()
        }

        return PublicationResult(inserted, conflicts)
    }

    private fun factFields(fact: RegionalPriceFact): List<Any?> =
        listOf(
            fact.regionCode, fact.fuelCode, fact.price, fact.publicationDate, fact.sampleMinStationCount,
            fact.sampleDayCount, fact.sampleExpectedDayCount, fact.sampleCollectedAt, fact.sampleInputHash
        )
}
