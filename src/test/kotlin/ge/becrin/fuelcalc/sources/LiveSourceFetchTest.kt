package ge.becrin.fuelcalc.sources

import ge.becrin.fuelcalc.sources.benzup.BenzupImportService
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.service.OfficialImportService
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.RegionCatalogService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = ["imports.enabled=false", "imports.fetch-on-startup=false"])
@Testcontainers
@EnabledIfEnvironmentVariable(named = "LIVE_SOURCE_TEST", matches = "true")
class LiveSourceFetchTest {
    @Autowired
    private lateinit var regions: RegionCatalogService

    @Autowired
    private lateinit var catalog: RegionCatalog

    @Autowired
    private lateinit var official: OfficialImportService

    @Autowired
    private lateinit var market: BenzupImportService

    @Autowired
    private lateinit var properties: ImportProperties

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun testRealJsonEndpointsImportIntoIsolatedPostgres() {
        val today = LocalDate.now(ZoneId.of(properties.timeZone))
        properties.rosstat.startDate = today.minusWeeks(2)
        properties.rustata.startDate = properties.rosstat.startDate

        assertTrue(regions.refresh())
        assertTrue(catalog.regions.size >= 85)

        properties.enabled = true
        official.fetch()
        market.fetch()
        properties.enabled = false

        val officialCount = jdbc.queryForObject("SELECT COUNT(*) FROM fuel_prices WHERE source='OFFICIAL'", Long::class.java)
        val marketCount = jdbc.queryForObject("SELECT COUNT(*) FROM fuel_prices WHERE source='MARKET'", Long::class.java)

        assertTrue(checkNotNull(officialCount) >= 200, "No real official prices imported")
        assertTrue(checkNotNull(marketCount) >= 200, "No real market prices imported")
        assertEquals(listOf("ROSSTAT"), jdbc.queryForList(
            "SELECT DISTINCT provider FROM fuel_prices WHERE source='OFFICIAL'", String::class.java
        ))
        assertEquals(listOf(today.toString()), jdbc.queryForList(
            "SELECT DISTINCT reference_date::text FROM fuel_prices WHERE source='MARKET'", String::class.java
        ))

        println("Live JSON import verified: territories=${catalog.regions.size}, official=$officialCount, market=$marketCount, date=$today")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUSTATA_TOKEN", matches = ".+")
    fun testRealRustataFallbackIntoSameOfficialSeries() {
        val today = LocalDate.now(ZoneId.of(properties.timeZone))
        properties.rosstat.startDate = today.minusWeeks(2)
        properties.rustata.startDate = properties.rosstat.startDate

        assertTrue(regions.refresh())

        jdbc.update("DELETE FROM fuel_prices")
        jdbc.update("DELETE FROM import_states WHERE code='OFFICIAL'")

        val originalUrl = properties.rosstat.baseUrl
        properties.rosstat.baseUrl = "http://127.0.0.1:9"
        properties.enabled = true

        try {
            official.fetch()
        } finally {
            properties.rosstat.baseUrl = originalUrl
            properties.enabled = false
        }

        val count = jdbc.queryForObject("SELECT COUNT(*) FROM fuel_prices WHERE source='OFFICIAL'", Long::class.java)

        assertTrue(checkNotNull(count) >= 200, "No real rustata fallback prices imported")
        assertEquals(listOf("RUSTATA"), jdbc.queryForList("SELECT DISTINCT provider FROM fuel_prices", String::class.java))

        println("Live rustata fallback verified: official=$count")
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
