package ge.becrin.fuelcalc

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@SpringBootTest(properties = ["imports.enabled=false"])
@Testcontainers
@Transactional
class FuelCalcApplicationTest {
    @Autowired
    private lateinit var store: ImportStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun testUnchangedCatalogDoesNotWriteOrAdvanceRevision() {
        val regions = listOf(RegionCatalog.Region("03000000000", "Краснодарский край"))

        store.synchronizeRegions(regions, "first")

        val before = jdbc.queryForObject("SELECT revision FROM archive_state", Long::class.java)

        assertEquals(false, store.synchronizeRegions(regions, "first"))
        assertEquals(before, jdbc.queryForObject("SELECT revision FROM archive_state", Long::class.java))
    }

    @Test
    fun testWindowUpsertIsIdempotentAndProvidersAreInterchangeable() {
        store.synchronizeRegions(listOf(RegionCatalog.Region("03000000000", "Краснодарский край")), "one")

        val date = LocalDate.of(2026, 10, 19)
        val window = FetchWindow(date, date)
        val facts = listOf(FetchedPrice("03000000000", FuelType.DIESEL, date, "79.33".toBigDecimal()))

        assertEquals(1, store.saveWindow(SourceProvider.ROSSTAT, window, facts))
        assertEquals(0, store.saveWindow(SourceProvider.ROSSTAT, window, facts))
        assertEquals(1, store.saveWindow(SourceProvider.RUSTATA, window, facts))
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM fuel_prices", Long::class.java))
        assertEquals("OFFICIAL", jdbc.queryForObject("SELECT source FROM fuel_prices", String::class.java))
        assertEquals(date, store.fetchedThrough())
    }

    @Test
    fun testInvalidWindowCannotAdvanceCheckpoint() {
        val date = LocalDate.of(2026, 10, 19)

        assertFailsWith<InvalidDataAccessApiUsageException> {
            store.saveWindow(SourceProvider.ROSSTAT, FetchWindow(date, date), listOf(
                FetchedPrice("missing", FuelType.DIESEL, date.minusDays(1), "79".toBigDecimal())
            ))
        }

        assertNull(store.fetchedThrough())
    }

    @Test
    fun testCatalogRefreshPreservesHasDataAndNewRegionsStartEmpty() {
        val code = "03000000000"
        val date = LocalDate.of(2026, 10, 19)

        store.synchronizeRegions(listOf(RegionCatalog.Region(code, "Краснодарский край")), "initial")
        store.saveWindow(SourceProvider.ROSSTAT, FetchWindow(date, date), listOf(
            FetchedPrice(code, FuelType.DIESEL, date, "79".toBigDecimal())
        ))
        store.synchronizeRegions(listOf(
            RegionCatalog.Region(code, "Обновлённое имя"),
            RegionCatalog.Region("empty", "Пустой регион")
        ), "updated")

        assertEquals(listOf(code), jdbc.queryForList("SELECT okato_code FROM regions WHERE has_data", String::class.java))
        assertEquals(2, store.savedRegions().size)
    }

    @Test
    fun testOnlyNewSchemaRemainsAndRegionHasNoNameAliases() {
        val tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String::class.java)

        assertEquals(emptyList(), tables.filter { it in listOf("source_feeds", "price_periods", "benzup_measurements", "fuel_types") })

        val columns = jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='regions'",
            String::class.java
        ).toSet()

        assertEquals(setOf("id", "okato_code", "name", "has_data"), columns)
        assertEquals(listOf("OFFICIAL", "MARKET"), PriceSource.entries.map { it.name })
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
