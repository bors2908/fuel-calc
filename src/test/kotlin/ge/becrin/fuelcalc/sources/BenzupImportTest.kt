package ge.becrin.fuelcalc.sources

import com.sun.net.httpserver.HttpServer
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.benzup.BenzupArchiveService
import ge.becrin.fuelcalc.sources.benzup.BenzupImportService
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest(properties = [
    "imports.enabled=true", "imports.fetch-on-startup=false",
    "imports.rosstat.enabled=false", "imports.rustata.enabled=false",
    "imports.rosstat.cron=-", "imports.rustata.cron=-", "imports.benzup.cron=-"
])
@Import(SourceTestClockConfiguration::class)
@Testcontainers
@Transactional
class BenzupImportTest {
    @Autowired
    private lateinit var importer: BenzupImportService

    @Autowired
    private lateinit var archive: BenzupArchiveService

    @Autowired
    private lateinit var store: ImportStore

    @Autowired
    private lateinit var catalog: RegionCatalog

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var entities: EntityManager

    @BeforeEach
    fun prepare() {
        store.synchronizeRegions(listOf(RegionCatalog.Region(REGION, "Краснодарский край")))
        catalog.replace(store.savedRegions())
        body.set(snapshot("60"))
    }

    @Test
    fun testFetchUpdatesOneDailyRowWithArithmeticMeanOfAllSnapshots() {
        importer.fetch()
        entities.flush()

        val firstId = jdbc.queryForObject("SELECT id FROM benzup_daily_prices", Long::class.java)
        body.set(snapshot("90"))
        importer.fetch()
        body.set(snapshot("60"))
        importer.fetch()
        entities.flush()

        assertEquals(3L, count("benzup_measurements"))
        assertEquals(1L, count("benzup_daily_prices"))
        assertEquals(firstId, jdbc.queryForObject("SELECT id FROM benzup_daily_prices", Long::class.java))
        assertEquals(3, jdbc.queryForObject("SELECT measurement_count FROM benzup_daily_prices", Int::class.java))
        assertEquals(
            BigDecimal("70.000000"),
            jdbc.queryForObject("SELECT price_rub_per_liter FROM benzup_daily_prices", BigDecimal::class.java)
        )
        assertEquals(BigDecimal("70.000000"), jdbc.queryForObject(
            "SELECT o.price_rub_per_liter FROM regional_price_keys k " +
                "JOIN regional_price_observations o ON o.id = k.current_observation_id " +
                "JOIN price_periods p ON p.id = k.period_id WHERE k.provider_code = 'BENZUP' AND p.frequency = 'DAILY'",
            BigDecimal::class.java
        ))
        assertEquals(3L, count("regional_price_observations"))
    }

    @Test
    fun testRepeatedRunDoesNotCountTwiceAndMoscowMidnightStartsNewDailyRow() {
        val before = Instant.parse("2026-09-01T20:59:59Z")
        val run = record("60", before)
        archive.record(run, mapOf((REGION to "DIESEL") to BigDecimal("60")), before)
        record("90", before.plusSeconds(1))
        entities.flush()

        assertEquals(2L, count("benzup_measurements"))
        assertEquals(2L, count("benzup_daily_prices"))
        assertEquals(
            listOf(1, 1),
            jdbc.queryForList("SELECT measurement_count FROM benzup_daily_prices ORDER BY observed_date", Int::class.java)
        )
    }

    @Test
    fun testWeeklyAndMonthlyAggregatesGiveDailyMeansEqualWeight() {
        val at = Instant.parse("2026-09-01T10:00:00Z")
        record("60", at)
        record("90", at.plusSeconds(3600))
        record("60", at.plusSeconds(7200))
        record("80", at.plusSeconds(86400))
        entities.flush()

        for (frequency in listOf(PriceFrequency.WEEKLY, PriceFrequency.MONTHLY)) {
            val fact = archive.prepare(frequency, LocalDate.of(2026, 10, 1), null, "https://benzup.ru/index-region")
                .first().facts.single()

            assertEquals(BigDecimal("75.000000"), fact.price)
            assertEquals(4, fact.sampleMeasurementCount)
            assertEquals(2, fact.sampleDayCount)
            assertEquals(if (frequency == PriceFrequency.WEEKLY) 7 else 30, fact.sampleExpectedDayCount)
        }
    }

    @Test
    fun testFailurePreservesHistoryAndDoesNotContributeToDailyAverage() {
        importer.fetch()
        body.set("{\"result\":[],\"error\":1}")
        importer.fetch()
        entities.flush()

        assertEquals(1L, count("benzup_measurements"))
        assertEquals(
            BigDecimal("60.000000"),
            jdbc.queryForObject("SELECT price_rub_per_liter FROM benzup_daily_prices", BigDecimal::class.java)
        )
        assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM import_runs WHERE status = 'FAILED'", Long::class.java))

        body.set(snapshot("80"))
        importer.fetch()
        entities.flush()

        assertEquals(2L, count("benzup_measurements"))
        assertEquals(
            BigDecimal("70.000000"),
            jdbc.queryForObject("SELECT price_rub_per_liter FROM benzup_daily_prices", BigDecimal::class.java)
        )
    }

    @Test
    fun testProviderOnlyRegionIsSkippedWithoutDiscardingKnownRegionPrices() {
        val mixed = snapshot("60").substringBeforeLast(']') + "," + unknownRegionSnapshot().substringAfter('[')

        body.set(mixed)
        importer.fetch()
        entities.flush()

        assertNull(catalog.byName("Новый регион"))
        assertEquals(1L, count("regions"))
        assertEquals(1L, count("benzup_measurements"))
        assertEquals(1L, count("benzup_daily_prices"))
        assertEquals(
            BigDecimal("60.000000"),
            jdbc.queryForObject("SELECT price_rub_per_liter FROM benzup_daily_prices", BigDecimal::class.java)
        )
        assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM import_runs WHERE status = 'SUCCEEDED'", Long::class.java))
    }

    @Test
    fun testSnapshotWithoutRosstatRegionsFailsWithoutChangingSavedPrices() {
        importer.fetch()
        body.set(unknownRegionSnapshot())
        importer.fetch()
        entities.flush()

        assertNull(catalog.byName("Новый регион"))
        assertEquals(1L, count("regions"))
        assertEquals(1L, count("benzup_measurements"))
        assertEquals(1L, count("benzup_daily_prices"))
        assertEquals(1, jdbc.queryForObject("SELECT measurement_count FROM benzup_daily_prices", Int::class.java))
        assertEquals(
            BigDecimal("60.000000"),
            jdbc.queryForObject("SELECT price_rub_per_liter FROM benzup_daily_prices", BigDecimal::class.java)
        )
        assertEquals(3L, jdbc.queryForObject(
            "SELECT COUNT(*) FROM import_runs WHERE status = 'FAILED' AND error_code = 'EMPTY_PUBLICATION'",
            Long::class.java
        ))
    }

    private fun unknownRegionSnapshot(): String =
        snapshot("75").replace("Краснодарский край", "Новый регион").replace("\"region_id\":23", "\"region_id\":999")

    private fun record(
        price: String,
        at: Instant
    ): Long {
        val run = checkNotNull(store.begin(importer.definition(PriceFrequency.DAILY)))
        archive.record(run, mapOf((REGION to "DIESEL") to BigDecimal(price)), at)

        return run
    }

    private fun count(table: String): Long =
        checkNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java))

    companion object {
        private const val REGION = "03000000000"

        private val body = AtomicReference(snapshot("60"))

        private val source = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
            server.createContext("/") { exchange ->
                val bytes = body.get().toByteArray()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()
        }

        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun sourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("imports.benzup.endpoint") { "http://127.0.0.1:${source.address.port}/index/regional" }
        }

        @JvmStatic
        @AfterAll
        fun stopSource() {
            source.stop(0)
        }

        private fun snapshot(price: String): String =
            """{"result":[{"region_id":23,"region_name":"Краснодарский край","product":"D0DT0","index":"$price"}],"error":0}"""
    }
}
