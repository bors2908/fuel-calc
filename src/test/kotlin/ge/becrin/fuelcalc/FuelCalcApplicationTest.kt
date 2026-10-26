package ge.becrin.fuelcalc

import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import ge.becrin.fuelcalc.fuels.jpa.FuelTypeEntity
import ge.becrin.fuelcalc.fuels.jpa.FuelTypeRepository
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.PricePeriodEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceObservationEntity
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.sources.PriceAggregationMethod
import ge.becrin.fuelcalc.sources.jpa.DataSourceEntity
import ge.becrin.fuelcalc.sources.jpa.DataSourceRepository
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
import org.springframework.context.ApplicationContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.Repository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
class FuelCalcApplicationTest {
    private data class PriceFixture(
        val key: RegionalPriceKeyEntity,

        val observation: RegionalPriceObservationEntity,

        val publication: SourcePublicationEntity
    )

    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var fuelTypes: FuelTypeRepository

    @Autowired
    private lateinit var sources: DataSourceRepository

    @Autowired
    private lateinit var archive: ArchiveStateRepository

    @Autowired
    private lateinit var liquibase: SpringLiquibase

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Value("\${local.server.port}")
    private var port: Int = 0

    private val loadedAt = Instant.parse("2026-10-26T09:10:00.123456Z")

    private val price = BigDecimal("76.123456789012345678")

    @Test
    fun testStartsHealthyWithAllJpaRepositories() {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/actuator/health"))
            .timeout(Duration.ofSeconds(10))
            .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertContains(response.body(), "\"status\":\"UP\"")
        assertEquals(12, entityManager.metamodel.entities.size)
        assertEquals(12, context.getBeansOfType(Repository::class.java).size)
    }

    @Test
    fun testSeedsOnlyCatalogsAndArchiveRevision() {
        assertEquals(setOf("DIESEL", "AI_92", "AI_95"), fuelTypes.findAll().map { it.code }.toSet())
        assertEquals(setOf("ROSSTAT", "RUSTATA", "WHEREFUEL"), sources.findAll().map { it.code }.toSet())
        assertEquals(1L, archive.findById(1).orElseThrow().revision)
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
        assertEquals(7L, jdbc.queryForObject("SELECT COUNT(DISTINCT filename) FROM db_migration_log", Long::class.java))
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

        for (sourceCode in listOf("RUSTATA", "WHEREFUEL")) {
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
                "UPDATE regional_price_observations SET sample_min_station_count = 5 WHERE id = ?",
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
            endDate = LocalDate.of(2026, 10, 19),
            label = "19 октября 2026"
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
