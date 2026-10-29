package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyRepository
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import jakarta.persistence.EntityManager
import ge.becrin.fuelcalc.prices.service.RegionalPricePublicationService
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.context.annotation.Import
import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.dto.FuelPriceQueryDto
import ge.becrin.fuelcalc.prices.service.FuelPriceTrendService
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.PriceSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    private lateinit var publisher: RegionalPricePublicationService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var counter: SqlCounter

    @Autowired
    private lateinit var trendService: FuelPriceTrendService

    @Autowired
    private lateinit var archive: ArchiveReadService

    @Autowired
    private lateinit var keys: RegionalPriceKeyRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var fixture: ArchiveFixture

    private val contract = ApiContractAssertions()

    @BeforeEach
    fun prepare() {
        fixture = ArchiveFixture(store, publisher, keys)
        fixture.regions()
    }

    @Test
    fun testSqlCountsAreBoundedAndDoNotGrowPerRegionOrPoint() {
        for (region in listOf("A", "B", "C", "D")) {
            fixture.price(region, "72.50", date = "2026-10-12")
            fixture.price(region, "73.50")
        }

        val counts = listOf("A", "A,B", "A,B,C,D").map { selected ->
            entityManager.flush()
            entityManager.clear()
            counter.reset()
            prices("trend", regions = selected)

            counter.count()
        }

        assertTrue(counts.all { it <= 7 }, counts.toString())
        assertEquals(counts.first(), counts.last())

        entityManager.flush()
        entityManager.clear()
        counter.reset()
        prices("trend", regions = "A,B,C,D", extra = mapOf("filterDateFrom" to "2026-10-19"))

        assertEquals(counts.last(), counter.count())

        println("API SELECT counts: trend 1/2/4 regions=$counts")

        val comparisonCounts = listOf("A,B", "A,B,C,D").map { selected ->
            entityManager.flush()
            entityManager.clear()
            counter.reset()
            prices("comparison", regions = selected)

            counter.count()
        }

        assertTrue(comparisonCounts.all { it <= 9 }, comparisonCounts.toString())
        assertEquals(comparisonCounts.first(), comparisonCounts.last())

        println("API SELECT counts: comparison 2/4 regions=$comparisonCounts")

        entityManager.flush()
        entityManager.clear()
        counter.reset()
        prices("overview", regions = "A,B,C,D")

        assertTrue(counter.count() <= 9)

        entityManager.flush()
        entityManager.clear()
        counter.reset()
        calculate(regions = listOf("A", "B", "C", "D"))

        assertTrue(counter.count() <= 9)

        entityManager.flush()
        entityManager.clear()
        counter.reset()
        fetch("/api/v1/fuel-prices/coverage", mapOf("filterRegionCode" to "A,B,C,D", "preferredSource" to "ROSSTAT"))

        assertTrue(counter.count() <= 6)

        val input = FuelPriceQueryDto().also {
            it.baseRegionCode = "A"
            it.filterRegionCode = "A,B,C,D"
            it.filterFuelType = FuelType.DIESEL
            it.filterDateFrom = LocalDate.of(2026, 9, 1)
            it.filterDateTo = LocalDate.of(2026, 10, 26)
            it.frequency = PriceFrequency.WEEKLY
            it.preferredSource = PriceSource.ROSSTAT
        }
        val dto = trendService.get(input)
        entityManager.flush()
        entityManager.clear()
        counter.reset()
        json.writeValueAsString(dto)

        assertEquals(0, counter.count())
    }

    @Test
    fun testJpaPublicationLoadsRelationsInSetsAndKeepsExistingVersions() {
        val feed = FeedDefinition("ROSSTAT_JPA_TEST", "ROSSTAT", "ROSSTAT", PriceFrequency.WEEKLY, "jpa-test")
        store.initialize(listOf(feed))

        val runId = checkNotNull(store.begin(feed))
        val counts = listOf(listOf("A"), listOf("A", "B", "C", "D")).mapIndexed { index, selected ->
            val date = LocalDate.of(2026, 10, 12).plusWeeks(index.toLong())
            val price = "72.50".toBigDecimal()
            val publication = PreparedPublication(
                period = PricePeriod(PriceFrequency.WEEKLY, date, date.minusDays(6), date),
                externalKey = "jpa-test:$date",
                url = "https://example.test/jpa",
                attribution = "Test data",
                facts = selected.map { RegionalPriceFact(it, "DIESEL", price, date.plusDays(2)) }
            )
            entityManager.flush()
            entityManager.clear()
            counter.reset()

            val result = publisher.publish(feed, runId, publication)
            val selects = counter.count()
            val observationId = keys.currentObservationId("A", date, PriceFrequency.WEEKLY, "ROSSTAT")

            assertEquals(selected.size, result.inserted)
            assertEquals(0, result.conflicts)
            entityManager.flush()
            entityManager.clear()

            val repeated = publisher.publish(feed, runId, publication)

            assertEquals(0, repeated.inserted)
            assertEquals(0, repeated.conflicts)
            assertEquals(observationId, keys.currentObservationId("A", date, PriceFrequency.WEEKLY, "ROSSTAT"))

            selects
        }

        assertEquals(counts.first(), counts.last(), counts.toString())
        assertTrue(counts.all { it <= 8 }, counts.toString())
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun testHttpReadDoesNotMixRevisionAndPricesDuringConcurrentPublication() {
        store.synchronizeRegions(listOf(RegionCatalog.Region("TX_A", "Регион транзакционного теста")))
        fixture.price("TX_A", "10.00")
        val snapshotStarted = CountDownLatch(1)
        val published = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "api-snapshot-test") }
        val isolation = archive.read {
            jdbc.queryForObject("SHOW transaction_isolation", String::class.java)
        }

        assertEquals("repeatable read", isolation)
        counter.afterSelect = { sql ->
            if (Thread.currentThread().name == "api-snapshot-test" && sql.contains("from archive_state", ignoreCase = true)) {
                snapshotStarted.countDown()
                check(published.await(10, TimeUnit.SECONDS))
            }
        }

        try {
            val pending = executor.submit<JsonNode> { prices("overview", regions = "TX_A", base = "TX_A") }
            assertTrue(snapshotStarted.await(10, TimeUnit.SECONDS))

            fixture.price("TX_A", "12.00", published = "2026-10-22")
            published.countDown()

            val old = pending.get(10, TimeUnit.SECONDS)
            val current = prices("overview", regions = "TX_A", base = "TX_A")

            assertEquals("10.00", old.path("latestPrice").path("priceRubPerLiter").asString())
            assertEquals("12.00", current.path("latestPrice").path("priceRubPerLiter").asString())
            assertFalse(old.path("meta").path("dataVersion").asString() == current.path("meta").path("dataVersion").asString())
        } finally {
            counter.afterSelect = null
            published.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun testArchiveAndUnexpectedFailuresHaveSafeContractErrors() {
        val secret = "private-database-diagnostic"

        try {
            counter.afterSelect = { throw DataAccessResourceFailureException(secret) }
            val unavailable = fetch("/api/v1/fuel-types", status = 503)

            assertEquals("ServiceUnavailable", unavailable.path("type").asString())
            assertFalse(unavailable.toString().contains(secret))

            counter.afterSelect = { throw IllegalStateException(secret) }
            val unexpected = fetch("/api/v1/fuel-types", status = 500)

            assertEquals("InternalError", unexpected.path("type").asString())
            assertFalse(unexpected.toString().contains(secret))
        } finally {
            counter.afterSelect = null
        }
    }

    @Test
    fun testReadEndpointsAndCalculationDoNotChangeTheArchive() {
        val id = fixture.price("A", "72.50")
        fixture.price("B", "71.80")
        val version = jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java)
        val observations = jdbc.queryForObject("SELECT COUNT(*) FROM regional_price_observations", Long::class.java)

        fetch("/api/v1/regions")
        fetch("/api/v1/fuel-types")
        fetch("/api/v1/fuel-prices/coverage", mapOf("filterRegionCode" to "A,B", "preferredSource" to "ROSSTAT"))
        prices("overview")
        prices("trend")
        prices("comparison")
        fetch("/api/v1/fuel-prices/observations/$id")
        calculate()

        assertEquals(version, jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java))
        assertEquals(observations, jdbc.queryForObject("SELECT COUNT(*) FROM regional_price_observations", Long::class.java))
    }

    @Test
    fun testFallbackUsesTheWholeDeclaredChainAndMarksAllMinimumTies() {
        fixture.price("A", "70", provider = "RUSTATA")
        fixture.price("A", "60", provider = "BENZUP")
        fixture.price("B", "80")
        fixture.price("B", "60", provider = "BENZUP")
        fixture.price("C", "80")
        fixture.price("C", "70", provider = "RUSTATA")
        fixture.price("D", "60", provider = "BENZUP")

        for ((region, preferred, expected) in listOf(
            Triple("A", "ROSSTAT", "RUSTATA"),
            Triple("B", "RUSTATA", "ROSSTAT"),
            Triple("C", "BENZUP", "RUSTATA"),
            Triple("D", "ROSSTAT", "BENZUP")
        )) {
            val row = prices("comparison", regions = region, base = region, preferred = preferred)
                .path("comparison").path("data").path(0)

            assertEquals(expected, row.path("price").path("source").path("provider").path("code").asString())
        }

        fixture.price("B", "70", published = "2026-10-22")
        val rows = prices("comparison", regions = "A,B,C").path("comparison").path("data").toList()

        assertEquals(2, rows.count { it.path("isLowest").asBoolean() })
        assertEquals(1, rows.count { it.path("isHighest").asBoolean() })
    }

    @Test
    fun testCompleteDictionariesAndCoverageOfEmptyArchive() {
        store.synchronizeRegions(listOf(RegionCatalog.Region("PERCENT", "Регион 100%")))
        val regions = fetch("/api/v1/regions").path("data").toList()

        assertEquals(5, regions.size)
        assertTrue(regions.any { it.path("code").asString() == "PERCENT" && it.path("name").asString() == "Регион 100%" })
        assertEquals(
            regions.sortedWith(compareBy({ it.path("name").asString() }, { it.path("code").asString() })),
            regions
        )
        assertEquals(3, fetch("/api/v1/fuel-types").path("data").size())

        val coverage = fetch("/api/v1/fuel-prices/coverage", mapOf("filterRegionCode" to "A,B,C,D", "preferredSource" to "ROSSTAT"))

        assertEquals(36, coverage.path("data").size())
        assertTrue(coverage.path("data").all { it.path("observationCount").asInt() == 0 && it.path("firstAvailableDate").isNull })
        assertTrue(coverage.path("data").all { !it.has("latestSource") })
    }

    @Test
    fun testEveryPreferredSourceWinsWithoutAveraging() {
        for ((source, price) in listOf("ROSSTAT" to "80.50", "RUSTATA" to "70.25", "BENZUP" to "60.125000")) {
            fixture.price("A", price, provider = source)
        }

        for ((source, price) in listOf("ROSSTAT" to "80.50", "RUSTATA" to "70.25", "BENZUP" to "60.125000")) {
            val result = prices("comparison", regions = "A", preferred = source).path("comparison")
            val cell = result.path("data").path(0).path("price")

            assertEquals("SINGLE_REGION", result.path("status").asString())
            assertEquals(price, cell.path("priceRubPerLiter").asString())
            assertEquals(source, cell.path("source").path("provider").path("code").asString())
            assertEquals(source == "BENZUP", cell.path("source").has("sample"))
            assertTrue(cell.path("missingReason").isNull)
        }
    }

    @Test
    fun testFallbackAndUnavailableSourcePreserveSavedPricesAndPublisher() {
        fixture.price("A", "80")
        val proxyId = fixture.price("B", "70", provider = "RUSTATA")
        jdbc.update("UPDATE source_feeds SET update_status = 'UNAVAILABLE', enabled = FALSE WHERE provider_code = 'ROSSTAT'")
        entityManager.clear()
        val response = prices("comparison")
        val comparison = response.path("comparison")
        val rows = comparison.path("data").associateBy { it.path("region").path("code").asString() }

        assertEquals("AVAILABLE", comparison.path("status").asString())
        assertEquals("ROSSTAT", rows.getValue("A").path("price").path("source").path("provider").path("code").asString())
        val updateKey = rows.getValue("A").path("price").path("source").path("updateKey").asString()

        assertEquals("UNAVAILABLE", response.path("sourceUpdates").path(updateKey).path("status").asString())
        assertEquals("RUSTATA", rows.getValue("B").path("price").path("source").path("provider").path("code").asString())
        val provenance = fetch("/api/v1/fuel-prices/observations/$proxyId").path("observation")

        assertEquals("ROSSTAT", provenance.path("publisher").path("code").asString())
        assertEquals("RUSTATA", provenance.path("price").path("source").path("provider").path("code").asString())
    }

    @Test
    fun testComparisonUsesOlderCommonPeriodAndOverviewKeepsIndividualLatest() {
        fixture.price("A", "72.50", date = "2026-10-12")
        fixture.price("B", "71.80", date = "2026-10-12")
        fixture.price("A", "74.00")
        val comparison = prices("comparison").path("comparison")
        val overview = prices("overview")

        assertEquals("2026-10-12", comparison.path("period").path("referenceDate").asString())
        assertTrue(comparison.path("hasNewerObservations").asBoolean())
        assertEquals("2026-10-19", overview.path("latestPrice").path("period").path("referenceDate").asString())
        assertEquals("2026-10-12", overview.path("comparison").path("period").path("referenceDate").asString())
        assertFalse(overview.path("change").has("endPrice"))
        assertFalse(overview.path("comparison").has("reason"))
        assertFalse(overview.path("comparison").has("message"))
        assertEquals("1.50", overview.path("change").path("absoluteRubPerLiter").asString())
        assertEquals("2.07", overview.path("change").path("percent").asString())
    }

    @Test
    fun testNoCommonPeriodKeepsAllRowsAndChangesPriceSort() {
        fixture.price("A", "80", date = "2026-10-12")
        fixture.price("B", "70")
        val result = prices("comparison", regions = "A,B,C", extra = mapOf("sortOrder" to "DESC")).path("comparison")

        assertEquals("NO_COMMON_PERIOD", result.path("status").asString())
        assertEquals("REGION_NAME", result.path("sortBy").asString())
        assertEquals("ASC", result.path("sortOrder").asString())
        assertEquals(3, result.path("data").size())
        assertTrue(result.path("snapshotId").isNull)
        assertFalse(result.has("period"))
        assertTrue(result.path("data").all { it.path("differenceRubPerLiter").isNull })
        val missing = result.path("data").path(2).path("price")

        assertTrue(missing.path("priceRubPerLiter").isNull)
        assertTrue(missing.path("observationId").isNull)
        assertFalse(missing.has("source"))
    }

    @Test
    fun testTrendReturnsCommonCalendarAndExplicitMissingPoints() {
        fixture.price("A", "80", date = "2026-10-12")
        fixture.price("A", "81")
        fixture.price("B", "70", provider = "RUSTATA")
        val response = prices("trend", regions = "A,B,C")
        val data = response.path("data")

        assertEquals("AVAILABLE", data.path(0).path("status").asString())
        assertEquals("PARTIAL", data.path(1).path("status").asString())
        assertEquals("NO_DATA", data.path(2).path("status").asString())
        assertTrue(data.all { it.path("points").size() == 2 })
        assertEquals(2, response.path("sourceUpdates").size())
        assertEquals("2026-10-19", data.path(0).path("lastAvailableDate").asString())

        for (series in data) {
            for (point in series.path("points")) {
                assertFalse(point.has("freshness"))

                if (!point.path("priceRubPerLiter").isNull) {
                    val source = point.path("source")
                    val key = source.path("updateKey").asString()

                    assertTrue(response.path("sourceUpdates").has(key))
                    assertFalse(source.has("publisher"))
                    assertFalse(source.has("datasetId"))
                    assertFalse(source.has("publicationId"))
                    assertFalse(source.path("provider").has("url"))
                }
            }
        }
        assertEquals("NO_OBSERVATION_FOR_PERIOD", data.path(1).path("points").path(0).path("missingReason").asString())
        assertTrue(data.path(1).path("points").path(0).has("period"))
        assertEquals("RUSTATA", data.path(1).path("points").path(1).path("source").path("provider").path("code").asString())
    }

    @Test
    fun testComparisonEqualityAndSortAreDeterministic() {
        fixture.price("A", "72.50")
        fixture.price("B", "72.500")
        val first = prices("comparison").path("comparison")
        val second = prices("comparison", regions = "B,A", extra = mapOf("sortOrder" to "DESC")).path("comparison")

        assertTrue(first.path("allPricesEqual").asBoolean())
        assertTrue(first.path("data").all { !it.path("isLowest").asBoolean() && !it.path("isHighest").asBoolean() })
        assertEquals(listOf("A", "B"), second.path("data").toList().map { it.path("region").path("code").asString() })
        assertEquals(first.path("snapshotId").asString(), second.path("snapshotId").asString())
        assertEquals(2, prices("overview").path("comparison").path("lowestRegions").size())
    }

    @Test
    fun testRevokedCurrentPointerFallsBackWhileOldObservationRemainsReadable() {
        val old = fixture.price("A", "80.00")
        fixture.price("A", "70.00", provider = "RUSTATA")
        jdbc.update("UPDATE regional_price_keys SET current_observation_id = NULL WHERE provider_code = 'ROSSTAT'")
        entityManager.clear()
        store.bumpRevision()

        val current = prices("overview", regions = "A").path("latestPrice")

        assertEquals("RUSTATA", current.path("source").path("provider").path("code").asString())
        assertEquals(
            "80.00",
            fetch("/api/v1/fuel-prices/observations/$old").path("observation").path("price")
            .path("priceRubPerLiter").asString()
        )
    }

    @Test
    fun testFreshnessUsesWholeArchiveAndFrequenciesRemainIndependent() {
        fixture.price("A", "80", date = "2026-09-28")
        fixture.price("A", "81")
        fixture.price("A", "99", date = "2026-09-30", frequency = PriceFrequency.MONTHLY)
        val result = prices("overview", regions = "A", extra = mapOf("filterDateTo" to "2026-09-30"))

        assertEquals("80", result.path("latestPrice").path("priceRubPerLiter").asString())
        assertEquals("2026-10-19", result.path("latestAvailableDate").asString())
        assertFalse(result.path("latestPrice").has("freshness"))
        val coverage = fetch(
            "/api/v1/fuel-prices/coverage",
            mapOf(
                "filterRegionCode" to "A",
                "preferredSource" to "ROSSTAT",
                "filterFuelType" to "DIESEL"
            )
        )

        assertEquals(listOf(0, 2, 1), coverage.path("data").toList().map { it.path("observationCount").asInt() })

        val month = prices("overview", regions = "A", extra = mapOf("frequency" to "MONTHLY")).path("latestPrice")

        assertEquals("99", month.path("priceRubPerLiter").asString())
        assertEquals("Сентябрь 2026", month.path("period").path("label").asString())
    }

    @Test
    fun testZeroBaseAndEmptyOrSinglePointChanges() {
        assertEquals("NO_DATA", prices("comparison", regions = "A").path("comparison").path("status").asString())
        fixture.price("A", "0", date = "2026-10-12")
        assertEquals("INSUFFICIENT_DATA", prices("overview", regions = "A").path("change").path("status").asString())
        fixture.price("A", "2")
        val change = prices("overview", regions = "A").path("change")

        assertEquals("ZERO_BASE", change.path("status").asString())
        assertEquals("2", change.path("absoluteRubPerLiter").asString())
        assertTrue(change.path("percent").isNull)
    }

    @Test
    fun testRefuelingIsSelfContainedAndDifferenceUsesRoundedTotals() {
        fixture.price("A", "1.004")
        fixture.price("B", "1.005")
        val result = calculate(volume = "1")
        val comparison = result.path("comparison")
        val rows = comparison.path("data").associateBy { it.path("region").path("code").asString() }
        val expectedComparison = prices("comparison").path("comparison")

        assertEquals("1.00", result.path("baseTotalRub").asString())
        assertEquals("0.01", rows.getValue("B").path("differenceRub").asString())
        assertEquals("HIGHER", rows.getValue("B").path("relation").asString())
        assertEquals("BASE", rows.getValue("A").path("relation").asString())
        assertFalse(rows.getValue("A").has("isBase"))
        assertFalse(result.has("data"))
        assertFalse(result.has("expectedSnapshotId"))
        assertTrue(result.path("snapshotChanged").isNull)
        assertEquals(expectedComparison.path("snapshotId"), comparison.path("snapshotId"))
        assertEquals(expectedComparison.path("period"), comparison.path("period"))

        for (expected in expectedComparison.path("data")) {
            val row = rows.getValue(expected.path("region").path("code").asString())

            for (field in listOf("price", "differenceRubPerLiter", "isLowest", "isHighest", "latestAvailableDate")) {
                assertEquals(expected.path(field), row.path(field))
            }
        }
    }

    @Test
    fun testSnapshotChangesOnlyForSelectionAndUsedPrices() {
        fixture.price("A", "72.50")
        fixture.price("B", "71.80")
        val original = prices("comparison")
        val snapshot = original.path("comparison").path("snapshotId").asString()
        val version = original.path("meta").path("dataVersion").asString()
        fixture.price("D", "90")

        assertEquals(snapshot, prices("comparison").path("comparison").path("snapshotId").asString())
        assertFalse(calculate(expected = snapshot).path("snapshotChanged").asBoolean())
        val conflict = prices("trend", extra = mapOf("dataVersion" to version), status = 409)

        assertEquals("DataVersionChanged", conflict.path("type").asString())
        assertTrue(conflict.has("currentDataVersion"))
        fixture.price("A", "73.00", published = "2026-10-22")

        assertTrue(calculate(expected = snapshot).path("snapshotChanged").asBoolean())
        val changedSource = prices("comparison", preferred = "RUSTATA")

        assertFalse(original.path("context").path("key").asString() == changedSource.path("context").path("key").asString())
    }

    @Test
    fun testObservationDetailsKeepsOldImmutableVersionAndUnknownReturns404() {
        val old = fixture.price("A", "72.50")
        fixture.price("A", "73.00", published = "2026-10-22")
        val result = fetch("/api/v1/fuel-prices/observations/$old").path("observation")

        assertEquals("72.50", result.path("price").path("priceRubPerLiter").asString())
        assertEquals(old.toString(), result.path("price").path("observationId").asString())
        assertTrue(result.path("publishedAt").isNull)
        assertFalse(result.has("observationId"))
        assertEquals("ROSSTAT", result.path("publisher").path("code").asString())
        assertEquals("fixture-ROSSTAT-WEEKLY", result.path("datasetId").asString())
        assertEquals("2026-10-21", result.path("publicationDate").asString())
        assertEquals("https://example.test/prices", result.path("publicationUrl").asString())
        assertTrue(result.path("publicationId").isNull)
        assertEquals("ResourceNotFound", fetch("/api/v1/fuel-prices/observations/not-an-id", status = 404).path("type").asString())
        fetch("/api/v1/fuel-prices/observations/99999999999999999999999", status = 404)
    }

    @Test
    fun testInputValidationRejectsAmbiguousCsvAndInvalidSelection() {
        for (codes in listOf("A,A", "A,,B", "A,", "A,B,C,D,E", "A, B", "UNKNOWN")) {
            prices("trend", regions = codes, status = 400)
        }
        prices("trend", regions = "B", status = 400)
        prices("trend", preferred = "UNKNOWN", status = 400)
        prices("trend", extra = mapOf("filterFuelType" to "UNKNOWN"), status = 400)
        prices("trend", extra = mapOf("filterDateTo" to "2999-01-01"), status = 400)
        prices("trend", extra = mapOf("filterDateFrom" to "2026-10-25", "filterDateTo" to "2026-09-01"), status = 400)
        val request = get("/api/v1/fuel-prices/coverage").queryParam("filterRegionCode", "A", "B")
            .queryParam("preferredSource", "ROSSTAT")
        val response = mvc.perform(request).andReturn().response

        assertEquals(400, response.status)
        assertEquals("no-store", response.getHeader("Cache-Control"))
    }

    @Test
    fun testJsonVolumeBoundsAndStrictTypes() {
        fixture.price("A", "72.50")
        for (value in listOf<Any>(600, true, "1e2", "0", "00.1", "1.001", "1000000.01", " 600", "6,5")) {
            calculate(volume = value, regions = listOf("A"), status = 400)
        }
        calculate(volume = "0.01", regions = listOf("A"))
        calculate(volume = "1000000.00", regions = listOf("A"))
        calculate(volume = "1", regions = listOf("A"), expected = null, includeExpected = true, status = 400)
        calculate(volume = "1", regions = listOf("A"), extra = mapOf("price" to "1"), status = 400)
        calculate(
            volume = "1",
            extra = mapOf(
                "selection" to mapOf(
                    "baseRegionCode" to "A",
                    "regionCodes" to listOf("A", null),
                    "fuelType" to "DIESEL",
                    "dateFrom" to "2026-09-01",
                    "dateTo" to "2026-10-26",
                    "frequency" to "WEEKLY",
                    "preferredSource" to "ROSSTAT"
                )
            ),
            status = 400
        )
        for (override in listOf(mapOf("fuelType" to 0), mapOf("dateFrom" to listOf(2026, 9, 1)))) {
            calculate(
                volume = "1",
                extra = mapOf(
                    "selection" to (
                        mapOf(
                            "baseRegionCode" to "A",
                            "regionCodes" to listOf("A"),
                            "fuelType" to "DIESEL",
                            "dateFrom" to "2026-09-01",
                            "dateTo" to "2026-10-26",
                            "frequency" to "WEEKLY",
                            "preferredSource" to "ROSSTAT"
                        ) + override
                    )
                ),
                status = 400
            )
        }
        calculate(regions = listOf("A", "B"), status = 422)
        calculate(regions = listOf("B"), base = "B", status = 422)
    }

    @Test
    fun testDailyBenzupPriceIncludesMeasurementProvenance() {
        fixture.price("A", "75.123456", date = "2026-10-26", provider = "BENZUP", frequency = PriceFrequency.DAILY)

        val result = prices("overview", regions = "A", extra = mapOf("frequency" to "DAILY", "preferredSource" to "BENZUP"))
        val price = result.path("latestPrice")

        assertEquals("75.123456", price.path("priceRubPerLiter").asString())
        assertEquals("DAILY", price.path("period").path("frequency").asString())
        assertEquals("BENZUP", price.path("source").path("provider").path("code").asString())
        assertEquals("REGIONAL_INDEX_MEAN", price.path("source").path("aggregationMethod").asString())
        assertEquals(5, price.path("source").path("sample").path("measurementCount").asInt())
    }

    private fun prices(
        endpoint: String,
        regions: String = "A,B",
        base: String = "A",
        preferred: String = "ROSSTAT",
        extra: Map<String, String> = emptyMap(),
        status: Int = 200
    ): JsonNode =
        fetch(
            "/api/v1/fuel-prices/$endpoint",
            mapOf(
                "baseRegionCode" to base,
                "filterRegionCode" to regions,
                "filterFuelType" to "DIESEL",
                "filterDateFrom" to "2026-09-01",
                "filterDateTo" to "2026-10-26",
                "frequency" to "WEEKLY",
                "preferredSource" to preferred
            ) + extra,
            status
        )

    private fun fetch(
        path: String,
        parameters: Map<String, String> = emptyMap(),
        status: Int = 200
    ): JsonNode {
        val request = get(path)
        parameters.forEach { (key, value) -> request.queryParam(key, value) }
        val response = mvc.perform(request).andReturn().response

        assertEquals(status, response.status, response.contentAsString)
        assertEquals("no-store", response.getHeader("Cache-Control"))

        val value = json.readTree(response.contentAsString)
        contract.response(path, status, value)

        return value
    }

    private fun calculate(
        volume: Any = "600",
        regions: List<String> = listOf("A", "B"),
        base: String = "A",
        expected: String? = null,
        includeExpected: Boolean = false,
        extra: Map<String, Any> = emptyMap(),
        status: Int = 200
    ): JsonNode {
        val body = mutableMapOf<String, Any?>(
            "selection" to mapOf(
                "baseRegionCode" to base,
                "regionCodes" to regions,
                "fuelType" to "DIESEL",
                "dateFrom" to "2026-09-01",
                "dateTo" to "2026-10-26",
                "frequency" to "WEEKLY",
                "preferredSource" to "ROSSTAT"
            ),
            "volumeLiters" to volume
        )

        if (includeExpected || expected != null) {
            body["expectedSnapshotId"] = expected
        }

        body.putAll(extra)

        val response = mvc.perform(
            post("/api/v1/refueling/calculate").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body))
        ).andReturn().response

        assertEquals(status, response.status, response.contentAsString)
        assertEquals("no-store", response.getHeader("Cache-Control"))

        val value = json.readTree(response.contentAsString)
        contract.response("/api/v1/refueling/calculate", status, value)

        return value
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
