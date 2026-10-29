package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.config.BusinessCalendar
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.jpa.FuelPriceEntity
import ge.becrin.fuelcalc.prices.jpa.FuelPriceRepository
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.regions.model.Region
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.SourceTestClockConfiguration
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStateRepository
import ge.becrin.fuelcalc.sources.jpa.PriceImportStore
import ge.becrin.fuelcalc.sources.jpa.RegionImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.FetchedPrice
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceImportCompleted
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(properties = ["imports.enabled=false", "imports.benzup.test-backfill-enabled=true"])
@Import(SourceTestClockConfiguration::class)
@Testcontainers
@ExtendWith(OutputCaptureExtension::class)
class BenzupTestBackfillTest {
    @Autowired
    private lateinit var market: BenzupImportService

    @MockitoBean
    private lateinit var client: BenzupClient

    @Autowired
    private lateinit var mapping: BenzupRegionMapping

    @Autowired
    private lateinit var catalog: RegionCatalog

    @Autowired
    private lateinit var regionStore: RegionImportStore

    @Autowired
    private lateinit var priceStore: PriceImportStore

    @Autowired
    private lateinit var prices: FuelPriceRepository

    @Autowired
    private lateinit var regions: RegionRepository

    @Autowired
    private lateinit var states: ImportStateRepository

    @Autowired
    private lateinit var properties: ImportProperties

    @Autowired
    private lateinit var calendar: BusinessCalendar

    @Autowired
    private lateinit var events: ApplicationEventPublisher

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private lateinit var regionId: String

    private lateinit var otherRegionId: String

    private lateinit var regionCode: String

    private lateinit var otherRegionCode: String

    private val start = LocalDate.of(2026, 10, 5)

    @BeforeEach
    fun prepare() {
        jdbc.update("DELETE FROM fuel_prices")
        jdbc.update("DELETE FROM import_states")
        jdbc.update("DELETE FROM regions")

        val selected = mapping.okatoByRegionId.entries.distinctBy { it.value }.take(2)
        regionId = selected[0].key
        regionCode = selected[0].value
        otherRegionId = selected[1].key
        otherRegionCode = selected[1].value
        properties.enabled = true
        properties.benzup.startDate = start
        regionStore.synchronizeRegions(listOf(Region(regionCode, "Регион А"), Region(otherRegionCode, "Регион Б")), "backfill-test")
        catalog.replace(regionStore.savedRegions())
        whenever(client.fetch()).thenReturn(listOf(BenzupPrice(regionId, FuelType.DIESEL, "66.00".toBigDecimal())))
    }

    @Test
    fun testBackfillUsesEarliestObservationAndIndependentRegionFuelDeltas(output: CapturedOutput) {
        officialWeeks(regionCode, FuelType.DIESEL, listOf("60.00", "61.00", "62.00", "63.00"))
        officialWeeks(regionCode, FuelType.AI_92, listOf("40.00", "41.00", "42.00", "43.00"))
        officialWeeks(otherRegionCode, FuelType.DIESEL, listOf("68.00", "69.00", "70.00", "71.00"))
        val earliest = start.plusDays(16)
        save(SourceProvider.BENZUP, listOf(
            FetchedPrice(regionCode, FuelType.DIESEL, earliest, "65.00".toBigDecimal()),
            FetchedPrice(otherRegionCode, FuelType.DIESEL, start.plusDays(19), "75.00".toBigDecimal())
        ))
        whenever(client.fetch()).thenReturn(listOf(
            BenzupPrice(regionId, FuelType.DIESEL, "99.00".toBigDecimal()),
            BenzupPrice(regionId, FuelType.AI_92, "55.00".toBigDecimal()),
            BenzupPrice(otherRegionId, FuelType.DIESEL, "99.00".toBigDecimal())
        ))

        market.fetch()

        assertGenerated(regionCode, FuelType.DIESEL, earliest, listOf("63.00", "64.00", "65.00"))
        assertGenerated(regionCode, FuelType.AI_92, calendar.today(), listOf("52.00", "53.00", "54.00"))
        assertGenerated(otherRegionCode, FuelType.DIESEL, start.plusDays(19), listOf("73.00", "74.00", "75.00"))
        val marketRows = marketRows()

        assertEquals("65.00", marketRows.single {
            it.region.okatoCode == regionCode && it.fuel == FuelType.DIESEL && it.referenceDate == earliest
        }.priceRubPerLiter.toPlainString())
        assertEquals("99.00", marketRows.single {
            it.region.okatoCode == regionCode && it.fuel == FuelType.DIESEL && it.referenceDate == calendar.today()
        }.priceRubPerLiter.toPlainString())
        assertEquals(calendar.today(), states.findById(PriceSource.MARKET.name).orElseThrow().fetchedThrough)
        assertTrue(output.all.contains("TEST-ONLY BENZUP BACKFILL: DO NOT USE IN PRODUCTION"))
    }

    @Test
    fun testSecondFetchDoesNotRewriteHistoryOrAdvanceArchiveRevision() {
        officialWeeks(regionCode, FuelType.DIESEL, listOf("60.00", "61.00", "62.00", "63.00"))

        market.fetch()

        val saved = marketRows().map { Triple(it.id, it.referenceDate, it.priceRubPerLiter) }
        val revision = revision()

        market.fetch()

        assertEquals(saved, marketRows().map { Triple(it.id, it.referenceDate, it.priceRubPerLiter) })
        assertEquals(revision, revision())
        assertEquals(start, marketRows().minOf { it.referenceDate })
    }

    @Test
    fun testHistoricalRowsAloneDoNotEnableBackfillBeforeSuccessfulFetch() {
        officialWeeks(regionCode, FuelType.DIESEL, listOf("60.00", "61.00", "62.00", "63.00"))
        prices.save(FuelPriceEntity(
            region = regions.findSelected(listOf(regionCode)).single(),
            fuel = FuelType.DIESEL,
            source = PriceSource.MARKET,
            frequency = PriceSource.MARKET.frequency,
            provider = SourceProvider.BENZUP,
            referenceDate = start.plusDays(16),
            priceRubPerLiter = "65.00".toBigDecimal(),
            loadedAt = Instant.parse("2026-10-26T12:00:00Z")
        ))

        events.publishEvent(SourceImportCompleted(PriceSource.OFFICIAL))

        assertEquals(1, marketRows().size)
        assertTrue(states.findById(PriceSource.MARKET.name).isEmpty)

        market.fetch()

        assertEquals(start, marketRows().minOf { it.referenceDate })
    }

    @Test
    fun testBackfillWaitsForAnchorWeekAndRetriesAfterOfficialImport() {
        officialWeeks(regionCode, FuelType.DIESEL, listOf("60.00", "61.00", "62.00"))

        market.fetch()

        assertEquals(1, marketRows().size)

        save(SourceProvider.RUSTATA, listOf(FetchedPrice(regionCode, FuelType.DIESEL, calendar.today(), "63.00".toBigDecimal())))
        events.publishEvent(SourceImportCompleted(PriceSource.OFFICIAL))

        assertEquals(22, marketRows().size)
        assertEquals(start, marketRows().minOf { it.referenceDate })
    }

    @Test
    fun testMissingOfficialWeeksAreNotExtendedIntoMissingDays() {
        save(SourceProvider.ROSSTAT, listOf(
            FetchedPrice(regionCode, FuelType.DIESEL, start, "60.00".toBigDecimal()),
            FetchedPrice(regionCode, FuelType.DIESEL, start.plusWeeks(2), "62.00".toBigDecimal()),
            FetchedPrice(regionCode, FuelType.DIESEL, calendar.today(), "63.00".toBigDecimal())
        ))

        market.fetch()

        val rows = marketRows()

        assertEquals(15, rows.size)
        assertTrue(rows.none { it.referenceDate in start.plusWeeks(1)..start.plusDays(13) })
        assertEquals("66.00", rows.single { it.referenceDate == calendar.today() }.priceRubPerLiter.toPlainString())
    }

    @Test
    fun testBackfillFailureDoesNotInvalidateSuccessfulMarketFetch(output: CapturedOutput) {
        officialWeeks(regionCode, FuelType.DIESEL, listOf("1.00", "1.00", "1.00", "100.00"))

        market.fetch()

        assertEquals(1, marketRows().size)
        assertEquals(calendar.today(), states.findById(PriceSource.MARKET.name).orElseThrow().fetchedThrough)
        assertNull(states.findById(PriceSource.MARKET.name).orElseThrow().errorMessage)
        assertTrue(output.all.contains("Test BenzUp backfill failed; successful source import retained"))
    }

    private fun assertGenerated(
        region: String,
        fuel: FuelType,
        anchor: LocalDate,
        expectedWeeks: List<String>
    ) {
        val rows = marketRows().filter { it.region.okatoCode == region && it.fuel == fuel && it.referenceDate < anchor }

        assertEquals((anchor.toEpochDay() - start.toEpochDay()).toInt(), rows.size)

        for (row in rows) {
            val week = ((row.referenceDate.toEpochDay() - start.toEpochDay()) / 7).toInt()
            val difference = row.priceRubPerLiter.subtract(expectedWeeks[week].toBigDecimal())

            assertTrue(difference.abs() <= BigDecimal("0.10"))
            assertTrue(difference.movePointRight(2).stripTrailingZeros().scale() <= 0)
            assertEquals(SourceProvider.BENZUP, row.provider)
            assertEquals(PriceSource.MARKET.frequency, row.frequency)
            assertNull(row.publicationDate)
        }
    }

    private fun officialWeeks(
        region: String,
        fuel: FuelType,
        weekly: List<String>
    ) {
        save(SourceProvider.ROSSTAT, weekly.mapIndexed { index, price ->
            FetchedPrice(region, fuel, start.plusWeeks(index.toLong()), price.toBigDecimal())
        })
    }

    private fun save(
        provider: SourceProvider,
        facts: List<FetchedPrice>
    ) {
        priceStore.saveWindow(provider, FetchWindow(facts.minOf { it.date }, facts.maxOf { it.date }), facts)
    }

    private fun marketRows(): List<FuelPriceEntity> =
        prices.findWindow(PriceSource.MARKET, start, calendar.today())

    private fun revision(): Long =
        checkNotNull(jdbc.queryForObject("SELECT revision FROM archive_state", Long::class.java))

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
