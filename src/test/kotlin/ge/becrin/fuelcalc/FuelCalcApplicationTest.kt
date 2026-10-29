package ge.becrin.fuelcalc

import ge.becrin.fuelcalc.sources.benzup.BenzupArchiveService
import ge.becrin.fuelcalc.prices.service.RegionalPricePublicationService
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.SourceImportException
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.fuels.jpa.FuelTypeEntity
import ge.becrin.fuelcalc.fuels.jpa.FuelTypeRepository
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.PricePeriodEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceObservationEntity
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.sources.PriceAggregationMethod
import ge.becrin.fuelcalc.sources.jpa.DataSourceEntity
import ge.becrin.fuelcalc.sources.jpa.ImportRunEntity
import ge.becrin.fuelcalc.sources.jpa.SourceFeedEntity
import ge.becrin.fuelcalc.sources.jpa.SourceFetchStateEntity
import ge.becrin.fuelcalc.sources.jpa.SourcePublicationEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import liquibase.integration.spring.SpringLiquibase
import org.hibernate.Hibernate
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["imports.enabled=false"])
@Testcontainers
@Transactional
class FuelCalcApplicationTest {
    private data class PriceFixture(
        val key: RegionalPriceKeyEntity,

        val observation: RegionalPriceObservationEntity,

        val publication: SourcePublicationEntity
    )

    @Autowired
    private lateinit var publisher: RegionalPricePublicationService

    @Autowired
    private lateinit var importStore: ImportStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var fuelTypes: FuelTypeRepository

    @Autowired
    private lateinit var liquibase: SpringLiquibase

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val loadedAt = Instant.parse("2026-10-26T09:10:00.123456Z")

    private val price = BigDecimal("76.123456789012345678")

    @Test
    fun testStartsHealthyWithValidatedJpaMappings() {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/actuator/health"))
            .timeout(Duration.ofSeconds(10))
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertContains(response.body(), "\"status\":\"UP\"")
        assertEquals(13, entityManager.metamodel.entities.size)
    }

    @Test
    fun testSeedsOnlyCatalogsAndArchiveRevision() {
        assertEquals(setOf("DIESEL", "AI_92", "AI_95"), fuelTypes.findAll().map { it.code }.toSet())
        assertEquals(setOf("ROSSTAT", "RUSTATA", "BENZUP"), jdbc.queryForList("SELECT code FROM data_sources", String::class.java).toSet())
        assertEquals(2L, jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM regions", Long::class.java))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM source_feeds", Long::class.java))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM regional_price_observations", Long::class.java))
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testSecondLiquibaseUpdateDoesNotChangeHistory() {
        val countBefore = jdbc.queryForObject("SELECT COUNT(*) FROM db_migration_log", Long::class.java)

        liquibase.afterPropertiesSet()

        assertEquals(countBefore, jdbc.queryForObject("SELECT COUNT(*) FROM db_migration_log", Long::class.java))
        assertEquals(9L, jdbc.queryForObject("SELECT COUNT(DISTINCT filename) FROM db_migration_log", Long::class.java))
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM archive_state", Long::class.java))
    }

    @Test
    fun testRoundTripsExactPriceDatesAndLazyRelationships() {
        val fixture = createPriceFixture()
        val observationId = fixture.observation.id

        entityManager.clear()

        val restored = entityManager.find(RegionalPriceObservationEntity::class.java, observationId)

        assertFalse(Hibernate.isInitialized(restored.priceKey))
        assertFalse(Hibernate.isInitialized(restored.publication))
        assertEquals(price, restored.priceRubPerLiter)
        assertEquals(loadedAt, restored.publication.loadedAt)
        assertEquals(LocalDate.of(2026, 10, 19), restored.priceKey.period.referenceDate)
        assertEquals(observationId, restored.priceKey.currentObservation?.id)
        assertEquals("ROSSTAT", restored.priceKey.provider.code)
    }

    @Test
    fun testKeepsSeparateCellsForAllThreeSources() {
        val fixture = createPriceFixture()

        for (sourceCode in listOf("RUSTATA", "BENZUP")) {
            entityManager.persist(
                RegionalPriceKeyEntity(
                    region = fixture.key.region,
                    fuelType = fixture.key.fuelType,
                    period = fixture.key.period,
                    provider = entityManager.getReference(DataSourceEntity::class.java, sourceCode),
                    createdAt = loadedAt
                )
            )
        }

        entityManager.flush()

        assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM regional_price_keys", Long::class.java))
    }

    @Test
    fun testRejectsDuplicateSourceCell() {
        val fixture = createPriceFixture()
        val error = assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                """
                INSERT INTO regional_price_keys (region_id, fuel_type_code, period_id, provider_code, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """.trimIndent(),
                fixture.key.region.id,
                fixture.key.fuelType.code,
                fixture.key.period.id,
                fixture.key.provider.code
            )
        }

        assertContains(error.mostSpecificCause.message.orEmpty(), "regional_price_keys_uk1")
    }

    @Test
    fun testRejectsCurrentObservationFromAnotherCell() {
        val fixture = createPriceFixture()
        val otherKey = RegionalPriceKeyEntity(
            region = fixture.key.region,
            fuelType = entityManager.getReference(FuelTypeEntity::class.java, "AI_92"),
            period = fixture.key.period,
            provider = fixture.key.provider,
            createdAt = loadedAt
        )

        entityManager.persist(otherKey)
        entityManager.flush()

        val error = assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "UPDATE regional_price_keys SET current_observation_id = ? WHERE id = ?",
                fixture.observation.id,
                otherKey.id
            )
        }

        assertContains(error.mostSpecificCause.message.orEmpty(), "regional_price_keys_fk4")
    }

    @ParameterizedTest
    @ValueSource(strings = ["-1", "NaN", "Infinity", "-Infinity"])
    fun testRejectsInvalidPrice(invalidPrice: String) {
        val fixture = createPriceFixture()
        val error = assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "UPDATE regional_price_observations SET price_rub_per_liter = CAST(? AS numeric) WHERE id = ?",
                invalidPrice,
                fixture.observation.id
            )
        }

        assertContains(error.mostSpecificCause.message.orEmpty(), "regional_price_observations_ck1")
    }

    @Test
    fun testRejectsIncompleteSampleMetadata() {
        val fixture = createPriceFixture()
        val error = assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "UPDATE regional_price_observations SET sample_measurement_count = 5 WHERE id = ?",
                fixture.observation.id
            )
        }

        assertContains(error.mostSpecificCause.message.orEmpty(), "regional_price_observations_ck2")
    }

    @Test
    fun testPersistsFetchStateWithSharedPrimaryKey() {
        val fixture = createPriceFixture()
        val feedId = fixture.publication.feed.id
        val state = SourceFetchStateEntity(feed = fixture.publication.feed, updatedAt = loadedAt)

        entityManager.persist(state)
        entityManager.flush()
        entityManager.clear()

        val restored = entityManager.find(SourceFetchStateEntity::class.java, feedId)

        assertNotNull(restored)
        assertEquals(feedId, restored.feedId)
        assertFalse(Hibernate.isInitialized(restored.feed))
        assertEquals(feedId, restored.feed.id)
    }

    @Test
    fun testRepeatedPublicationKeepsIdsAndDoesNotChangeRevision() {
        val feed = importFeed("ROSSTAT")
        val runId = checkNotNull(importStore.begin(feed))
        val publication = preparedPrice(BigDecimal("76.10"), LocalDate.of(2026, 10, 20))
        val first = publisher.publish(feed, runId, publication)
        val id = jdbc.queryForObject("SELECT current_observation_id FROM regional_price_keys", Long::class.java)
        val revision = jdbc.queryForObject("SELECT revision FROM archive_state", Long::class.java)
        val repeated = publisher.publish(
            feed, runId, publication.copy(facts = publication.facts.map { it.copy(price = BigDecimal("76.1000")) })
        )

        assertEquals(1, first.inserted)
        assertEquals(0, repeated.inserted)
        assertEquals(0, repeated.conflicts)
        assertEquals(id, jdbc.queryForObject("SELECT current_observation_id FROM regional_price_keys", Long::class.java))
        assertEquals(revision, jdbc.queryForObject("SELECT revision FROM archive_state", Long::class.java))
    }

    @Test
    fun testNewerPublicationPreservesHistoryAndUpdatesOnlyItsProvider() {
        val direct = importFeed("ROSSTAT")
        val proxy = importFeed("RUSTATA")
        val directRun = checkNotNull(importStore.begin(direct))
        val proxyRun = checkNotNull(importStore.begin(proxy))
        val original = preparedPrice(BigDecimal("76.10"), LocalDate.of(2026, 10, 20))

        publisher.publish(direct, directRun, original)
        publisher.publish(proxy, proxyRun, original)

        val originalId = jdbc.queryForObject(
            "SELECT current_observation_id FROM regional_price_keys WHERE provider_code = 'ROSSTAT'", Long::class.java
        )
        val corrected = preparedPrice(BigDecimal("77.25"), LocalDate.of(2026, 10, 21))
        val result = publisher.publish(direct, directRun, corrected)

        assertEquals(1, result.inserted)
        assertEquals(0, result.conflicts)
        assertEquals(BigDecimal("76.10"), jdbc.queryForObject(
            "SELECT price_rub_per_liter FROM regional_price_observations WHERE id = ?", BigDecimal::class.java, originalId
        ))
        assertEquals(BigDecimal("77.25"), jdbc.queryForObject(
            "SELECT o.price_rub_per_liter FROM regional_price_keys k " +
                "INNER JOIN regional_price_observations o ON o.id = k.current_observation_id WHERE k.provider_code = 'ROSSTAT'",
            BigDecimal::class.java
        ))
        assertEquals(BigDecimal("76.10"), jdbc.queryForObject(
            "SELECT o.price_rub_per_liter FROM regional_price_keys k " +
                "INNER JOIN regional_price_observations o ON o.id = k.current_observation_id WHERE k.provider_code = 'RUSTATA'",
            BigDecimal::class.java
        ))
    }

    @Test
    fun testUnorderedCorrectionIsArchivedWithoutReplacingCurrentPrice() {
        val feed = importFeed("ROSSTAT")
        val run = checkNotNull(importStore.begin(feed))
        val date = LocalDate.of(2026, 10, 20)

        publisher.publish(feed, run, preparedPrice(BigDecimal("76.10"), date))

        val result = publisher.publish(feed, run, preparedPrice(BigDecimal("75.00"), date))

        assertEquals(1, result.inserted)
        assertEquals(1, result.conflicts)
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM regional_price_observations", Long::class.java))
        assertEquals(BigDecimal("76.10"), jdbc.queryForObject(
            "SELECT o.price_rub_per_liter FROM regional_price_keys k " +
                "INNER JOIN regional_price_observations o ON o.id = k.current_observation_id", BigDecimal::class.java
        ))
    }

    @Test
    fun testRateLimitCooldownAppliesToBothFrequenciesAndSurvivesNextAttempt() {
        val feed = importFeed("RUSTATA")
        val monthly = feed.copy(code = "RUSTATA_MONTHLY_TEST", frequency = PriceFrequency.MONTHLY)
        importStore.initialize(listOf(monthly))
        val run = checkNotNull(importStore.begin(feed))
        val retryAt = Instant.now().plusSeconds(3600)

        importStore.finish(run, 0, SourceImportException("RATE_LIMITED", "Rate limited", retryAt), "UNKNOWN")

        assertEquals(null, importStore.begin(feed))
        assertEquals(null, importStore.begin(monthly))
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM import_runs WHERE id = ?", String::class.java, run))
        assertEquals("UNAVAILABLE", jdbc.queryForObject(
            "SELECT update_status FROM source_feeds WHERE code = ?", String::class.java, feed.code
        ))
    }

    @Test
    fun testHistoryProgressCanStartAdvanceAndComplete() {
        val feed = importFeed("ROSSTAT")
        val start = LocalDate.of(2026, 1, 1)
        val until = LocalDate.of(2026, 10, 1)

        importStore.saveProgress(feed, "BACKFILL", start, until)

        assertEquals(start.toString(), importStore.state(feed).backfillNextFrom.toString())

        importStore.saveProgress(feed, "BACKFILL", null, null)

        assertEquals(null, importStore.state(feed).backfillNextFrom)
        assertNotNull(importStore.state(feed).backfillCompletedAt)

        importStore.saveProgress(feed, "RECONCILIATION", start, until)
        importStore.saveProgress(feed, "RECONCILIATION", null, null)

        assertNotNull(importStore.state(feed).lastReconciledAt)
    }

    @Test
    fun testRegionCatalogUpsertUpdatesNamesOnlyWhenChangedAndRetainsHistory() {
        val first = RegionCatalog.Region("01000000000", "Тестовая область")
        val second = RegionCatalog.Region("02000000000", "Тестовый край")
        val revision = checkNotNull(jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java))
        importStore.synchronizeRegions(listOf(first, second))

        assertEquals(revision + 1, jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java))
        assertEquals(listOf(first, second), importStore.savedRegions())

        importStore.synchronizeRegions(listOf(first, second))

        assertEquals(revision + 1, jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java))

        val renamed = first.copy(name = "Обновлённая тестовая область")
        importStore.synchronizeRegions(listOf(renamed))

        assertEquals(listOf(renamed, second), importStore.savedRegions())
        assertEquals(revision + 2, jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java))
    }

    private fun importFeed(provider: String): FeedDefinition {
        val feed = FeedDefinition(
            "${provider}_WEEKLY_TEST", provider, if (provider == "BENZUP") provider else "ROSSTAT", PriceFrequency.WEEKLY,
            "test-source", if (provider == "BENZUP") BenzupArchiveService.POLICY else null
        )
        importStore.initialize(listOf(feed))
        importStore.synchronizeRegions(listOf(RegionCatalog.Region("03000000000", "Тестовый край")))

        return feed
    }

    private fun preparedPrice(
        price: BigDecimal,
        publicationDate: LocalDate
    ): PreparedPublication =
        PreparedPublication(
            PricePeriod(PriceFrequency.WEEKLY, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 19)),
            "test-week-2026-10-19", "https://example.test/prices", "Test source",
            listOf(RegionalPriceFact("03000000000", "DIESEL", price, publicationDate))
        )

    private fun createPriceFixture(): PriceFixture {
        val region = RegionEntity(code = "23", name = "Краснодарский край", createdAt = loadedAt)
        val provider = entityManager.getReference(DataSourceEntity::class.java, "ROSSTAT")
        val feed = SourceFeedEntity(
            code = "ROSSTAT_WEEKLY_TEST",
            publisher = provider,
            provider = provider,
            externalCode = "test-weekly-prices",
            frequency = PriceFrequency.WEEKLY,
            aggregationMethod = PriceAggregationMethod.OFFICIAL_STATISTICS
        )
        val run = ImportRunEntity(feed = feed, startedAt = loadedAt.minusSeconds(1))
        val publication = SourcePublicationEntity(
            feed = feed,
            firstImportRun = run,
            externalKey = "2026-10-19",
            contentHash = "a".repeat(64),
            loadedAt = loadedAt,
            attribution = "Тестовая публикация"
        )
        val period = PricePeriodEntity(
            periodKey = "WEEKLY:2026-10-19",
            frequency = PriceFrequency.WEEKLY,
            referenceDate = LocalDate.of(2026, 10, 19),
            startDate = LocalDate.of(2026, 10, 13),
            endDate = LocalDate.of(2026, 10, 19)
        )
        val key = RegionalPriceKeyEntity(
            region = region,
            fuelType = entityManager.getReference(FuelTypeEntity::class.java, "DIESEL"),
            period = period,
            provider = provider,
            createdAt = loadedAt
        )
        val observation = RegionalPriceObservationEntity(
            priceKey = key,
            publication = publication,
            priceRubPerLiter = price
        )

        for (entity in listOf(region, feed, run, publication, period, key, observation)) {
            entityManager.persist(entity)
        }

        key.currentObservation = observation
        entityManager.flush()

        return PriceFixture(key, observation, publication)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
