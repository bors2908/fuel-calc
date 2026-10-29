package ge.becrin.fuelcalc.sources

import com.sun.net.httpserver.HttpServer
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.benzup.BenzupImportService
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStateStore
import ge.becrin.fuelcalc.sources.jpa.PriceImportStore
import ge.becrin.fuelcalc.sources.jpa.RegionImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.OfficialImportService
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.RegionCatalogService
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import ge.becrin.fuelcalc.sources.benzup.backfill.BenzupTestBackfill
import ge.becrin.fuelcalc.sources.benzup.backfill.BenzupTestBackfillStore
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(properties = ["imports.enabled=false", "imports.benzup.test-backfill-enabled=false"])
@Import(SourceTestClockConfiguration::class)
@Testcontainers
class SourceImportTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var stateStore: ImportStateStore

    @Autowired
    private lateinit var regionStore: RegionImportStore

    @Autowired
    private lateinit var official: OfficialImportService

    @Autowired
    private lateinit var market: BenzupImportService

    @Autowired
    private lateinit var catalogService: RegionCatalogService

    @Autowired
    private lateinit var catalog: RegionCatalog

    @Autowired
    private lateinit var properties: ImportProperties

    @Autowired
    private lateinit var store: PriceImportStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun prepare() {
        properties.enabled = true
        properties.rosstat.baseUrl = baseUrl
        properties.rosstat.startDate = LocalDate.of(2025, 1, 1)
        properties.rustata.baseUrl = baseUrl
        properties.rustata.startDate = properties.rosstat.startDate
        properties.rustata.token = "test-token"
        properties.rustata.requestInterval = Duration.ZERO
        properties.regions.okatoEndpoint = "$baseUrl/backbone/set/13032"
        properties.regions.selectionEndpoint = "$baseUrl/backbone/set/304297"
        properties.benzup.startDate = LocalDate.of(2026, 1, 1)
        properties.benzup.endpoint = "$baseUrl/market"
        failFromYear = null
        fallbackUnavailable = false
        invalidRustataPage = false
        catalogUnavailable = false
        malformedMask = false
        requests.clear()
        jdbc.update("DELETE FROM fuel_prices")
        jdbc.update("DELETE FROM import_states")
        jdbc.update("DELETE FROM regions")
        assertTrue(catalogService.refresh())
    }

    @Test
    fun testCatalogUsesSelectionReferencesAndPreservesExactCodesAndNames() {
        assertEquals(setOf("643", "030", "03000000000", "71140000000"), catalog.regions.map { it.okatoCode }.toSet())
        assertNull(catalog.byOkatoCode("03401000000"))
        assertTrue(catalog.byOkatoCode("71140000000")?.name.orEmpty().contains("Ямало-Ненецкий"))
    }

    @Test
    fun testUnchangedPayloadDoesNotUpdateDatabaseAndUnavailableCatalogRetainsHistory() {
        val revision = jdbc.queryForObject("SELECT revision FROM archive_state", Long::class.java)
        val saved = catalog.regions

        assertTrue(catalogService.refresh())
        assertEquals(revision, jdbc.queryForObject("SELECT revision FROM archive_state", Long::class.java))

        catalogUnavailable = true

        assertTrue(catalogService.refresh())
        assertEquals(saved, catalog.regions)
        assertEquals(saved.toSet(), regionStore.savedRegions().toSet())
    }

    @Test
    fun testBrokenMaskCannotReplaceCatalogOrItsHash() {
        val hash = jdbc.queryForObject("SELECT content_hash FROM import_states WHERE code='REGIONS'", String::class.java)
        val saved = regionStore.savedRegions()
        malformedMask = true

        assertTrue(catalogService.refresh())
        assertEquals(saved, regionStore.savedRegions())
        assertEquals(hash, jdbc.queryForObject("SELECT content_hash FROM import_states WHERE code='REGIONS'", String::class.java))
    }

    @Test
    fun testOfficialHistoryPagesByCalendarYearAndResumesFromSuccessfulCheckpoint() {
        official.fetch()

        assertEquals(LocalDate.of(2026, 10, 26), stateStore.fetchedThrough())
        assertEquals(setOf(2025, 2026), reportYears())
        assertEquals(1, requests.count { it == "/olap2/olap2-descriptor-report/274422/" })
        // One read belongs to catalog setup and one to this import, regardless of year count.
        assertEquals(2, requests.count { it == "/backbone/set/13032" })
        assertFalse(requests.any { it.startsWith("/api/v1/data") })
        assertTrue(countPrices() > 1000)

        val count = countPrices()
        requests.clear()

        official.fetch()

        assertEquals(setOf(2026), reportYears())
        assertEquals(1, requests.count { it == "/backbone/set/13032" })
        assertEquals(count, countPrices())
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(DISTINCT source) FROM fuel_prices", Long::class.java))
    }

    @Test
    fun testUnavailableRosstatUsesRustataWithoutDependingOnOfficialCalendar() {
        failFromYear = 2025

        official.fetch()

        assertEquals(LocalDate.of(2026, 10, 26), stateStore.fetchedThrough())
        assertEquals(2L, countPrices())
        assertEquals(setOf("RUSTATA"), jdbc.queryForList("SELECT DISTINCT provider FROM fuel_prices", String::class.java).toSet())
        assertEquals(setOf("OFFICIAL"), jdbc.queryForList("SELECT DISTINCT source FROM fuel_prices", String::class.java).toSet())
    }

    @Test
    fun testFailedYearCannotAdvanceCheckpointAndCompletedYearSurvivesRestart() {
        failFromYear = 2026
        fallbackUnavailable = true

        official.fetch()

        assertEquals(LocalDate.of(2025, 12, 31), stateStore.fetchedThrough())

        val completed = countPrices()

        assertTrue(completed > 0)
        assertEquals("2025", jdbc.queryForObject("SELECT EXTRACT(YEAR FROM MAX(reference_date))::text FROM fuel_prices", String::class.java))

        failFromYear = null
        requests.clear()

        official.fetch()

        assertEquals(LocalDate.of(2026, 10, 26), stateStore.fetchedThrough())
        assertTrue(countPrices() > completed)
        assertEquals(setOf(2025, 2026), reportYears())
    }

    @Test
    fun testIncompleteRustataPageDoesNotCommitPricesOrCheckpoint() {
        failFromYear = 2025
        invalidRustataPage = true

        official.fetch()

        assertNull(stateStore.fetchedThrough())
        assertEquals(0L, countPrices())
        assertTrue(jdbc.queryForObject(
            "SELECT error_message FROM import_states WHERE code='OFFICIAL'", String::class.java
        ).orEmpty().contains("pagination"))
    }

    @Test
    fun testMarketStoresOnlyTodayAndRetriesReplaceDailyCellWithoutAveraging() {
        market.fetch()

        val count = countPrices()

        assertTrue(count > 0)
        assertEquals(setOf("2026-10-26"), jdbc.queryForList("SELECT DISTINCT reference_date::text FROM fuel_prices", String::class.java).toSet())
        assertEquals(setOf("DAILY"), jdbc.queryForList("SELECT DISTINCT frequency FROM fuel_prices", String::class.java).toSet())
        assertEquals(setOf("MARKET"), jdbc.queryForList("SELECT DISTINCT source FROM fuel_prices", String::class.java).toSet())
        assertNull(stateStore.fetchedThrough())

        market.fetch()

        assertEquals(count, countPrices())
        assertFalse(requests.any { it.startsWith("REPORT:") })
    }

    @Test
    fun testFailedWindowRollsBackPricesAndRegionDataFlagsTogether() {
        val date = LocalDate.of(2026, 10, 26)
        val window = FetchWindow(date, date)
        val valid = FetchedPrice("03000000000", FuelType.DIESEL, date, "79.33".toBigDecimal())
        val invalid = FetchedPrice("643", FuelType.DIESEL, date, "9".repeat(70).toBigDecimal())

        assertFailsWith<DataIntegrityViolationException> {
            store.saveWindow(SourceProvider.ROSSTAT, window, listOf(valid, invalid))
        }

        assertEquals(0L, countPrices())
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM regions WHERE has_data", Long::class.java))
        assertNull(stateStore.fetchedThrough())

        store.saveWindow(SourceProvider.ROSSTAT, window, listOf(valid))

        assertEquals(listOf(valid.regionCode), jdbc.queryForList(
            "SELECT okato_code FROM regions WHERE has_data", String::class.java
        ))
    }

    @Test
    fun testFutureMarketStartDateDoesNotRequestOrStorePrices() {
        properties.benzup.startDate = LocalDate.of(2026, 10, 27)

        market.fetch()

        assertFalse(requests.any { it == "/market" })
        assertEquals(0L, countPrices())
        assertNull(stateStore.fetchedThrough(PriceSource.MARKET))
    }

    @Test
    fun testFallbackHonorsItsOwnStartDate() {
        failFromYear = 2025
        properties.rustata.startDate = LocalDate.of(2026, 1, 1)

        official.fetch()

        assertFalse(requests.any { it.startsWith("/api/v1/data") })
        assertEquals(0L, countPrices())
        assertNull(stateStore.fetchedThrough())
    }

    @Test
    fun testBackfillBeansAreAbsentWhenDisabled() {
        assertTrue(context.getBeansOfType(BenzupTestBackfill::class.java).isEmpty())
        assertTrue(context.getBeansOfType(BenzupTestBackfillStore::class.java).isEmpty())
    }

    private fun countPrices(): Long =
        checkNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM fuel_prices", Long::class.java))

    private fun reportYears(): Set<Int> =
        requests.filter { it.startsWith("REPORT:") }.map { it.substringAfter(':').toInt() }.toSet()

    companion object {
        private val json = JsonMapper.builder().build()

        private val requests = CopyOnWriteArrayList<String>()

        @Volatile
        private var failFromYear: Int? = null

        @Volatile
        private var fallbackUnavailable = false

        @Volatile
        private var invalidRustataPage = false

        @Volatile
        private var catalogUnavailable = false

        @Volatile
        private var malformedMask = false

        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
            server.createContext("/") { exchange ->
                try {
                    val path = exchange.requestURI.path
                    requests.add(exchange.requestURI.toString())
                    var status = 200
                    val response =
                        when (path) {
                            "/backbone/set/13032" -> {
                                if (catalogUnavailable) {
                                    status = 400
                                }

                                resource("rosstat/territories.json")
                            }
                            "/backbone/set/304297" -> if (malformedMask) "[{\"-298\":99999999}]" else resource("rosstat/selection.json")
                            "/backbone/set/108929" -> resource("rosstat/goods.json")
                            "/olap2/descr/report/274422/" -> {
                                exchange.responseHeaders.add("Set-Cookie", "csrftoken=public-session; Path=/")

                                "session"
                            }
                            "/olap2/olap2-descriptor-report/274422/" -> resource("rosstat/weekly-report.json")
                            "/olap2/olap2-preview/" -> {
                                require(exchange.requestHeaders.getFirst("X-CSRFToken") == "public-session")
                                require(exchange.requestHeaders.getFirst("Cookie").contains("csrftoken=public-session"))
                                require(exchange.requestHeaders.getFirst("Referer").startsWith(baseUrl))

                                val form = parameters(exchange.requestBody.bufferedReader().readText())
                                val request = json.readTree(checkNotNull(form["jkwargs"]))
                                val dates = values(request.path("col_header"), -64).toList().map { it.asString() }
                                val year = dates.first().take(4).toInt()
                                requests.add("REPORT:$year")

                                if (failFromYear?.let { year >= it } == true) {
                                    status = 400
                                }

                                val regions = values(request.path("row_header"), 13033).toList().map { it.asLong() }
                                val fuels = values(request.path("row_header"), 108930).toList().map { it.asLong() }
                                val rows = fuels.flatMap { fuel -> regions.map { region ->
                                    listOf("13033#eq#$region", "108930#eq#$fuel")
                                } }

                                json.writeValueAsString(mapOf(
                                    "headers" to mapOf("rowFilters" to rows, "colFilters" to dates.map { listOf("-64#eq#$it") }),
                                    "data" to mapOf("reportData" to mapOf("data" to rows.map { dates.map { mapOf("db_value" to "79.33") } }))
                                ))
                            }
                            "/api/v1/data" -> {
                                if (fallbackUnavailable) {
                                    status = 400
                                }

                                val query = parameters(exchange.requestURI.rawQuery)
                                val from = LocalDate.parse(query.getValue("start"))
                                val date = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                                val series = mapOf(
                                    "series_key" to "test", "geo_level" to "region", "geo" to "03000000000", "unit" to "RUB",
                                    "freq" to "W", "indicator" to "AVG_PRICE", "classification" to "7804"
                                )
                                val result = mutableMapOf<String, Any>(
                                    "series" to listOf(series),
                                    "observations" to listOf(mapOf("series_key" to "test", "period" to date.toString(), "value" to "80.50")),
                                    "offset" to 0, "series_total" to 1, "series_count" to 1, "observation_count" to 1,
                                    "has_more" to false
                                )

                                if (invalidRustataPage) {
                                    result.remove("has_more")
                                }

                                json.writeValueAsString(result)
                            }
                            "/market" -> resource("benzup/regional.json")
                            else -> {
                                status = 404

                                "{}"
                            }
                        }
                    val bytes = response.toByteArray(StandardCharsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(status, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } finally {
                    exchange.close()
                }
            }
            server.start()
        }

        private val baseUrl: String = "http://127.0.0.1:${server.address.port}"

        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        private fun resource(path: String): String =
            checkNotNull(SourceImportTest::class.java.getResourceAsStream("/sources/$path")).bufferedReader().use { it.readText() }

        private fun parameters(raw: String): Map<String, String> =
            raw.split('&').associate { part ->
                URLDecoder.decode(part.substringBefore('='), StandardCharsets.UTF_8) to
                    URLDecoder.decode(part.substringAfter('='), StandardCharsets.UTF_8)
            }

        private fun values(
            nodes: JsonNode,
            descriptor: Long
        ): JsonNode {
            for (node in nodes) {
                val field = node.path("descriptor")
                val id = if (field.isArray) field.path(0).asLong() else field.asLong()

                if (id == descriptor) {
                    return node.path("values")
                }

                val children = node.path("children")

                if (children.isArray) {
                    val found = values(children, descriptor)

                    if (!found.isMissingNode) {
                        return found
                    }
                }
            }

            return json.missingNode()
        }

        @JvmStatic
        @AfterAll
        fun stopSource() {
            server.stop(0)
        }
    }
}
