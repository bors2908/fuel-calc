package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.regions.service.RegionQueryService
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.jpa.ImportStateStore
import ge.becrin.fuelcalc.sources.jpa.PriceImportStore
import ge.becrin.fuelcalc.sources.jpa.RegionImportStore
import jakarta.persistence.EntityManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
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

@SpringBootTest(properties = ["imports.enabled=false"])
@AutoConfigureMockMvc
@Import(ApiTestConfiguration::class)
@Testcontainers
@Transactional
class BusinessApiTest {
    @Autowired
    private lateinit var counter: SqlCounter

    @Autowired
    private lateinit var entities: EntityManager

    @Autowired
    private lateinit var store: PriceImportStore

    @Autowired
    private lateinit var stateStore: ImportStateStore

    @Autowired
    private lateinit var regionStore: RegionImportStore

    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var json: JsonMapper

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var regions: RegionQueryService

    private lateinit var fixture: ArchiveFixture

    private val contract = ApiContractAssertions()

    @BeforeEach
    fun prepare() {
        fixture = ArchiveFixture(store, regionStore)
        fixture.regions()
    }

    @Test
    fun testRegionAndFuelContractsContainOnlyRequestedReferenceFields() {
        fixture.price("A", "72.50")

        val regions = read("/api/v1/regions").path("data")
        val fuels = read("/api/v1/fuel-types").path("data")

        assertEquals(setOf("okatoCode", "name"), regions.path(0).properties().map { it.key }.toSet())
        assertEquals(setOf("code", "name"), fuels.path(0).properties().map { it.key }.toSet())
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
        assertEquals(4, regionStore.savedRegions().size)
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

        val response = prices("trend", source = "MARKET")
        val series = response.path("data")

        assertEquals("NO_DATA", series.path(0).path("status").asString())
        assertEquals("2026-10-26", series.path(1).path("points").path(0).path("date").asString())
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
    fun testPageContainsBothSourcesAndCompactHistoryWithoutDuplicateContext() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")
        fixture.price("A", "80.12", "2026-10-20", SourceProvider.BENZUP)

        val response = page()
        val official = response.path("sources").path("OFFICIAL")
        val market = response.path("sources").path("MARKET")
        val point = official.path("trend").path(0).path("points").path(0)

        assertEquals("WEEKLY", official.path("frequency").asString())
        assertEquals("DAILY", market.path("frequency").asString())
        assertEquals(setOf("date", "priceRubPerLiter", "observationId", "missingReason"), point.properties().map { it.key }.toSet())
        assertEquals(false, official.has("context"))
        assertEquals(false, official.path("overview").has("meta"))
        assertEquals(false, official.path("comparison").has("snapshotId"))
        assertEquals(false, official.path("comparison").path("data").path(0).has("isHighest"))
        assertEquals(false, official.path("overview").path("comparison").has("basePriceRubPerLiter"))
    }

    @Test
    fun testRefuelingUsesBackendPricesAndUsesCurrentPrices() {
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

        assertEquals(listOf("OFFICIAL", "MARKET"), results.toList().map { it.path("source").asString() })
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

        val result = calculate(date = requestedDate)
        val results = result.path("results")

        assertEquals("42000.00", results.path(0).path("baseTotalRub").asString())
        assertEquals(officialDate, results.path(0).path("comparison").path("period").path("referenceDate").asString())
        assertEquals("48000.00", results.path(1).path("baseTotalRub").asString())
        assertEquals(marketDate, results.path(1).path("comparison").path("period").path("referenceDate").asString())
        assertEquals(requestedDate, result.path("context").path("dateFrom").asString())
        assertEquals(requestedDate, result.path("context").path("dateTo").asString())
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
    fun testObsoleteSnapshotInputAndInvalidVolumesAreRejected() {
        calculate(expected = """"expectedSnapshotIds":{"OFFICIAL":"test"},""", status = 400)
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
    fun testObsoletePriceRoutesAreRemoved() {
        for (endpoint in listOf("coverage", "overview", "trend", "comparison")) {
            assertEquals(404, mvc.perform(get("/api/v1/fuel-prices/$endpoint")).andReturn().response.status)
        }
    }

    @Test
    fun testNoDataIsAValidResponseAndUnavailableSourceRetainsHistory() {
        assertEquals("NO_DATA", prices("trend").path("data").path(0).path("status").asString())

        fixture.price("A", "72.50")
        stateStore.failed(PriceSource.OFFICIAL, IllegalStateException("unavailable"))

        val response = prices("overview")

        assertEquals("72.50", response.path("latestPrice").path("priceRubPerLiter").asString())
        assertEquals("UNAVAILABLE", page().path("sourceUpdates").path("OFFICIAL").path("status").asString())
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

    @Test
    fun testServerSortingReturnsBothSourcesWithSharedMetadata() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")
        fixture.price("A", "80.12", "2026-10-20", SourceProvider.BENZUP)
        fixture.price("B", "81.34", "2026-10-20", SourceProvider.BENZUP)

        val response = page(mapOf("sortSource" to "MARKET", "sortBy" to "PRICE", "sortOrder" to "DESC"))

        assertEquals("MARKET", response.path("comparisonOrder").asString())
        assertEquals("B", response.path("sources").path("MARKET").path("comparison").path("data").path(0)
            .path("region").path("okatoCode").asString())
        assertEquals(2, response.path("sources").size())
        assertEquals(false, response.path("sources").path("MARKET").has("meta"))
    }

    @Test
    fun testMarketIsDefaultOrderingWhenOfficialArchiveIsEmpty() {
        fixture.price("A", "80.12", "2026-10-20", SourceProvider.BENZUP)
        fixture.price("B", "81.34", "2026-10-20", SourceProvider.BENZUP)

        val response = page()

        assertEquals("MARKET", response.path("comparisonOrder").asString())
        assertEquals("NO_DATA", response.path("sources").path("OFFICIAL").path("comparison").path("status").asString())
        assertEquals("AVAILABLE", response.path("sources").path("MARKET").path("comparison").path("status").asString())
    }

    @Test
    fun testPricePageQueryCountStaysBoundedAcrossRegionsAndSources() {
        val selected = listOf("A", "B", "C", "D")

        for (region in selected) {
            fixture.price(region, "72.50")
            fixture.price(region, "73.50", provider = SourceProvider.BENZUP)
        }

        entities.flush()
        entities.clear()
        counter.reset()

        val response = page(mapOf("filterRegionCode" to selected.joinToString(",")))
        val selects = counter.count()

        assertEquals(selected.size, response.path("sources").path("OFFICIAL").path("trend").size())
        assertEquals(selected.size, response.path("sources").path("MARKET").path("trend").size())
        assertTrue(selects <= 5, "Price page issued $selects SELECTs; expected no per-region or per-source queries")
    }

    @Test
    fun testWeeklyPricesOverlapMidweekRangeWithoutLeakingDailyOrNeighbouringWeeks() {
        fixture.price("A", "70.00", "2026-10-12")
        fixture.price("A", "71.00", "2026-10-18")
        fixture.price("A", "72.50", "2026-10-19")
        fixture.price("B", "71.80", "2026-10-19")
        fixture.price("A", "73.00", "2026-10-26")
        fixture.price("A", "80.00", "2026-10-19", SourceProvider.BENZUP)
        fixture.price("A", "81.00", "2026-10-22", SourceProvider.BENZUP)

        val response = page(mapOf("filterDateFrom" to "2026-10-22", "filterDateTo" to "2026-10-24"))
        val official = response.path("sources").path("OFFICIAL")
        val market = response.path("sources").path("MARKET")
        val weekly = official.path("trend").path(0).path("points")
        val daily = market.path("trend").path(0).path("points")

        assertEquals("2026-10-22", response.path("context").path("dateFrom").asString())
        assertEquals("2026-10-24", response.path("context").path("dateTo").asString())
        assertEquals(1, weekly.size())
        assertEquals("2026-10-19", weekly.path(0).path("date").asString())
        assertEquals("72.50", weekly.path(0).path("priceRubPerLiter").asString())
        assertEquals("AVAILABLE", official.path("comparison").path("status").asString())
        assertEquals("72.50", official.path("overview").path("latestPrice").path("priceRubPerLiter").asString())
        assertEquals(1, daily.size())
        assertEquals("2026-10-22", daily.path(0).path("date").asString())
    }

    @Test
    fun testWeeklyPriceCoversSundayButDoesNotExtendIntoNextWeek() {
        fixture.price("A", "72.50", "2026-10-19")

        val sunday = page(mapOf("filterDateFrom" to "2026-10-25", "filterDateTo" to "2026-10-25"))
        val monday = page(mapOf("filterDateFrom" to "2026-10-26", "filterDateTo" to "2026-10-26"))

        assertEquals("72.50", sunday.path("sources").path("OFFICIAL").path("overview")
            .path("latestPrice").path("priceRubPerLiter").asString())
        assertEquals("NO_DATA", monday.path("sources").path("OFFICIAL").path("comparison").path("status").asString())
        assertEquals(0, monday.path("sources").path("OFFICIAL").path("trend").path(0).path("points").size())
    }

    private fun page(parameters: Map<String, String> = emptyMap()): JsonNode =
        read("/api/v1/fuel-prices", mapOf(
            "baseRegionCode" to "A", "filterRegionCode" to "A,B", "filterFuelType" to "DIESEL",
            "filterDateFrom" to "2026-10-01", "filterDateTo" to "2026-10-26"
        ) + parameters)

    private fun prices(
        endpoint: String,
        source: String = "OFFICIAL"
    ): JsonNode {
        val sourceData = page().path("sources").path(source)

        return when (endpoint) {
            "overview" -> sourceData.path("overview")
            "trend" -> json.createObjectNode().set("data", sourceData.path("trend"))
            "comparison" -> json.createObjectNode().set("comparison", sourceData.path("comparison"))
            else -> error("Unknown test section")
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
