package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.regions.service.RegionQueryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@SpringBootTest(properties = ["imports.enabled=false"])
@AutoConfigureMockMvc
@Import(ApiTestConfiguration::class)
@Testcontainers
@Transactional
class BusinessApiTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var store: ImportStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var regions: RegionQueryService

    private lateinit var fixture: ArchiveFixture

    private val contract = ApiContractAssertions()

    @BeforeEach
    fun prepare() {
        fixture = ArchiveFixture(store)
        fixture.regions()
    }

    @Test
    fun testRegionAndFuelContractsContainOnlyRequestedReferenceFields() {
        fixture.price("A", "72.50")

        val regions = read("/api/v1/regions").path("data")
        val fuels = read("/api/v1/fuel-types").path("data")

        assertEquals(setOf("okatoCode", "name"), regions.path(0).properties().map { it.key }.toSet())
        assertEquals(setOf("code", "name", "rosstatCode"), fuels.path(0).properties().map { it.key }.toSet())
        assertEquals(setOf("7802", "7803", "7804"), fuels.toList().map { it.path("rosstatCode").asString() }.toSet())
    }

    @Test
    fun testRegionListsExcludeEmptyRegionsByDefaultAndCanIncludeFullCatalog() {
        assertEquals(0, read("/api/v1/regions").path("data").size())
        assertEquals(emptyList(), regions.list().data)

        fixture.price("B", "81.00", provider = SourceProvider.BENZUP)
        fixture.price("A", "72.50")

        val expected = listOf("A", "B")

        assertEquals(expected, read("/api/v1/regions").path("data").toList().map { it.path("okatoCode").asString() })
        assertEquals(expected, read("/api/v1/regions", mapOf("includeEmptyRegions" to "false"))
            .path("data").toList().map { it.path("okatoCode").asString() })
        assertEquals(expected, regions.list().data.map { it.okatoCode })
        assertEquals(listOf("A", "B", "C", "D"), read("/api/v1/regions", mapOf("includeEmptyRegions" to "true"))
            .path("data").toList().map { it.path("okatoCode").asString() })
        assertEquals(4, regions.list(includeEmptyRegions = true).data.size)
        assertEquals(4, store.savedRegions().size)
    }

    @Test
    fun testInvalidIncludeEmptyRegionsIsRejected() {
        val result = mvc.perform(get("/api/v1/regions").param("includeEmptyRegions", "invalid")).andReturn()

        assertEquals(400, result.response.status)
        contract.response("/api/v1/regions", 400, json.readTree(result.response.contentAsString))
    }

    @Test
    fun testOfficialChannelsShareOnePriceAndExposeActualProviderInDetails() {
        fixture.price("A", "72.50")
        fixture.price("A", "73.00", provider = SourceProvider.RUSTATA)

        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM fuel_prices", Long::class.java))

        val overview = prices("overview")
        val price = overview.path("latestPrice")

        assertEquals("73.00", price.path("priceRubPerLiter").asString())
        assertEquals("OFFICIAL", price.path("source").asString())

        val details = read("/api/v1/fuel-prices/observations/${price.path("observationId").asString()}").path("observation")

        assertEquals("RUSTATA", details.path("provider").path("code").asString())
        assertTrue(details.has("loadedAt"))
    }

    @Test
    fun testMarketHasDailyPricesAndNeverFallsBackToOfficial() {
        fixture.price("A", "72.50")
        fixture.price("B", "81.00", date = "2026-10-26", provider = SourceProvider.BENZUP)

        val response = prices("trend", source = "MARKET", frequency = "DAILY")
        val series = response.path("data")

        assertEquals("NO_DATA", series.path(0).path("status").asString())
        assertEquals("MARKET", series.path(1).path("points").path(0).path("source").asString())
        assertEquals("DAILY", series.path(1).path("points").path(0).path("period").path("frequency").asString())
    }

    @Test
    fun testOverviewTrendAndComparisonUseCommonWeeklyPeriodAndPreciseAmounts() {
        fixture.price("A", "70.00", "2026-10-12")
        fixture.price("B", "69.00", "2026-10-12")
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")

        val overview = prices("overview")
        val comparison = prices("comparison")
        val trend = prices("trend")

        assertEquals("72.50", overview.path("latestPrice").path("priceRubPerLiter").asString())
        assertEquals("3.57", overview.path("change").path("percent").asString())
        assertEquals(2, trend.path("data").path(0).path("points").size())
        assertEquals("AVAILABLE", comparison.path("comparison").path("status").asString())
    }

    @Test
    fun testSnapshotChangesWhenSameObservationIsRevised() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")

        val before = prices("comparison").path("comparison").path("snapshotId").asString()

        fixture.price("A", "73.00")

        val after = prices("comparison").path("comparison").path("snapshotId").asString()

        assertNotEquals(before, after)
    }

    @Test
    fun testRefuelingUsesBackendPricesAndRejectsStaleSnapshot() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")

        val comparison = prices("comparison")
        val body = """{
            "selection":{"baseRegionCode":"A","regionCodes":["A","B"],"fuelType":"DIESEL",
                "date":"2026-10-26"},
            "volumeLiters":"600"
        }"""
        val result = mvc.perform(post("/api/v1/refueling/calculate").contentType("application/json").content(body)).andReturn()

        assertEquals(200, result.response.status, result.response.contentAsString)

        val response = json.readTree(result.response.contentAsString)
        contract.response("/api/v1/refueling/calculate", 200, response)

        assertTrue(response.toString().contains("43500.00"))
        assertTrue(response.toString().contains("43080.00"))
        assertTrue(comparison.has("comparison"))
    }

    @Test
    fun testRefuelingReturnsIndependentOfficialAndMarketTotalsAndDates() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")
        fixture.price("A", "80.12", "2026-10-20", SourceProvider.BENZUP)
        fixture.price("B", "81.34", "2026-10-20", SourceProvider.BENZUP)

        val response = calculate()
        val results = response.path("results")

        assertEquals(listOf("OFFICIAL", "MARKET"), results.toList().map { it.path("context").path("source").asString() })
        assertEquals("43500.00", results.path(0).path("baseTotalRub").asString())
        assertEquals("48072.00", results.path(1).path("baseTotalRub").asString())
        assertEquals("2026-10-19", results.path(0).path("comparison").path("period").path("referenceDate").asString())
        assertEquals("2026-10-20", results.path(1).path("comparison").path("period").path("referenceDate").asString())
        assertEquals("732.00", results.path(1).path("comparison").path("data").path(1).path("differenceRub").asString())
    }

    @Test
    fun testRefuelingUsesLatestCommonPricesAsOfDateAcrossAllSavedHistory() {
        val requestedDate = "2026-10-18"
        val officialDate = "2026-01-12"
        val marketDate = "2026-10-18"

        fixture.price("A", "70.00", officialDate)
        fixture.price("B", "69.00", officialDate)
        fixture.price("A", "72.50", "2026-10-12")
        fixture.price("A", "80.00", marketDate, SourceProvider.BENZUP)
        fixture.price("B", "81.00", marketDate, SourceProvider.BENZUP)
        fixture.price("A", "90.00", "2026-10-19")
        fixture.price("B", "91.00", "2026-10-19")
        fixture.price("A", "92.00", "2026-10-19", SourceProvider.BENZUP)
        fixture.price("B", "93.00", "2026-10-19", SourceProvider.BENZUP)

        val results = calculate(date = requestedDate).path("results")

        assertEquals("42000.00", results.path(0).path("baseTotalRub").asString())
        assertEquals(officialDate, results.path(0).path("comparison").path("period").path("referenceDate").asString())
        assertEquals("48000.00", results.path(1).path("baseTotalRub").asString())
        assertEquals(marketDate, results.path(1).path("comparison").path("period").path("referenceDate").asString())
        assertTrue(results.all {
            it.path("context").path("dateFrom").asString() == requestedDate &&
                it.path("context").path("dateTo").asString() == requestedDate
        })
    }

    @Test
    fun testRefuelingBeforeFirstObservationHasNoDataAndRejectsInvalidDate() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")

        val results = calculate(date = "2026-10-01").path("results")

        assertTrue(results.all { it.path("status").asString() == "NO_DATA" })

        val future = calculate(date = "2099-01-01", status = 400)

        assertTrue(future.toString().contains("selection.date"))
        calculate(date = "2026-02-30", status = 400)
    }

    @Test
    fun testMissingMarketDoesNotDiscardOfficialCalculation() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")

        val results = calculate().path("results")

        assertEquals("43500.00", results.path(0).path("baseTotalRub").asString())
        assertEquals("NO_DATA", results.path(1).path("status").asString())
        assertTrue(results.path(1).path("baseTotalRub").isNull)
        assertTrue(results.path(1).path("comparison").isNull)
    }

    @Test
    fun testOfficialWithoutCommonDateDoesNotDiscardMarketCalculation() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80", "2026-10-12")
        fixture.price("A", "80.12", "2026-10-20", SourceProvider.BENZUP)
        fixture.price("B", "81.34", "2026-10-20", SourceProvider.BENZUP)

        val results = calculate().path("results")

        assertEquals("NO_COMMON_PERIOD", results.path(0).path("status").asString())
        assertTrue(results.path(0).path("baseTotalRub").isNull)
        assertEquals("48072.00", results.path(1).path("baseTotalRub").asString())
    }

    @Test
    fun testNoPricesReturnsTwoUnavailableCalculations() {
        val results = calculate().path("results")

        assertEquals(2, results.size())
        assertTrue(results.all { it.path("status").asString() == "NO_DATA" && it.path("baseTotalRub").isNull })
    }

    @Test
    fun testExpectedSnapshotsAreComparedPerSourceAndInvalidInputIsRejected() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")
        fixture.price("A", "80.12", "2026-10-20", SourceProvider.BENZUP)
        fixture.price("B", "81.34", "2026-10-20", SourceProvider.BENZUP)

        val before = calculate().path("results")
        val officialId = before.path(0).path("comparison").path("snapshotId").asString()
        val marketId = before.path(1).path("comparison").path("snapshotId").asString()

        fixture.price("A", "73.00")

        val results = calculate(expected = """"expectedSnapshotIds":{"OFFICIAL":"$officialId","MARKET":"$marketId"},""")
            .path("results")

        assertTrue(results.path(0).path("snapshotChanged").asBoolean())
        assertEquals(false, results.path(1).path("snapshotChanged").asBoolean())
        calculate(expected = """"expectedSnapshotIds":{"OFFICIAL":""},""", status = 400)
        calculate(expected = """"expectedSnapshotIds":{"UNKNOWN":"test"},""", status = 400)
        calculate(volume = "0", status = 400)
        calculate(volume = "0.001", status = 400)
    }

    private fun calculate(
        expected: String = "",
        volume: String = "600",
        status: Int = 200,
        date: String = "2026-10-26"
    ): JsonNode {
        val body = """{
            "selection":{"baseRegionCode":"A","regionCodes":["A","B"],"fuelType":"DIESEL",
                "date":"$date"},
            $expected
            "volumeLiters":"$volume"
        }"""
        val result = mvc.perform(post("/api/v1/refueling/calculate").contentType("application/json").content(body)).andReturn()

        assertEquals(status, result.response.status, result.response.contentAsString)

        val response = json.readTree(result.response.contentAsString)

        contract.response("/api/v1/refueling/calculate", status, response)

        return response
    }

    @Test
    fun testMonthlyAndProviderSelectionsAreRejectedAndCoverageIsRemoved() {
        prices("trend", frequency = "MONTHLY", status = 400)
        prices("trend", source = "ROSSTAT", status = 400)
        prices("trend", source = "MARKET", frequency = "WEEKLY", status = 400)

        val result = mvc.perform(get("/api/v1/fuel-prices/coverage")).andReturn()

        assertEquals(404, result.response.status)
    }

    @Test
    fun testNoDataIsAValidResponseAndUnavailableSourceRetainsHistory() {
        assertEquals("NO_DATA", prices("trend").path("data").path(0).path("status").asString())

        fixture.price("A", "72.50")
        store.failed(PriceSource.OFFICIAL, IllegalStateException("unavailable"))

        val response = prices("overview")

        assertEquals("72.50", response.path("latestPrice").path("priceRubPerLiter").asString())
        assertEquals("UNAVAILABLE", response.path("sourceUpdates").path("OFFICIAL").path("status").asString())
    }

    private fun read(
        path: String,
        parameters: Map<String, String> = emptyMap()
    ): JsonNode {
        val request = get(path)

        parameters.forEach { (key, value) -> request.param(key, value) }

        val result = mvc.perform(request).andReturn()

        assertEquals(200, result.response.status, result.response.contentAsString)
        assertEquals("no-store", result.response.getHeader("Cache-Control"))

        val response = json.readTree(result.response.contentAsString)
        contract.response(path, 200, response)

        return response
    }

    private fun prices(
        endpoint: String,
        source: String = "OFFICIAL",
        frequency: String = "WEEKLY",
        status: Int = 200
    ): JsonNode {
        val path = "/api/v1/fuel-prices/$endpoint"
        val result = mvc.perform(get(path)
            .param("baseRegionCode", "A").param("filterRegionCode", "A,B").param("filterFuelType", "DIESEL")
            .param("filterDateFrom", "2026-10-01").param("filterDateTo", "2026-10-26")
            .param("frequency", frequency).param("source", source)).andReturn()

        assertEquals(status, result.response.status, result.response.contentAsString)

        val response = json.readTree(result.response.contentAsString)
        contract.response(path, status, response)

        return response
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
