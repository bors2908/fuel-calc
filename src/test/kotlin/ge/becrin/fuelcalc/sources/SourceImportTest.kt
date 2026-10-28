package ge.becrin.fuelcalc.sources

import com.sun.net.httpserver.HttpServer
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.PricePeriodEntity
import ge.becrin.fuelcalc.prices.jpa.PricePeriodRepository
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyRepository
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceObservationEntity
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.fuels.jpa.FuelTypeEntity
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.rosstat.RosstatMonthlyParser
import ge.becrin.fuelcalc.sources.rosstat.RosstatRegionCatalogParser
import ge.becrin.fuelcalc.sources.rosstat.RosstatCatalogParser
import ge.becrin.fuelcalc.sources.rosstat.RosstatWeeklyParser
import ge.becrin.fuelcalc.sources.rustata.RustataClient
import ge.becrin.fuelcalc.sources.rustata.RustataWeekResolver
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.RegionCatalogService
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import ge.becrin.fuelcalc.sources.wherefuel.RegionBoundary
import ge.becrin.fuelcalc.sources.wherefuel.RegionalStationSampler
import ge.becrin.fuelcalc.sources.wherefuel.StationPrice
import ge.becrin.fuelcalc.sources.wherefuel.WhereFuelClient
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.ServerSocket
import java.net.http.HttpConnectTimeoutException
import java.net.URI
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.sql.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceImportTest {
    private val regions = RegionCatalog().also {
        it.replace(fixtureRegions() + RegionCatalog.Region("45000000000", "Город федерального значения Москва"))
    }

    private val weekly = RosstatWeeklyParser(regions)

    private val now = Instant.parse("2026-10-26T12:00:00Z")

    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun testRosstatCatalogFindsSeparateDocumentTitlesAndDeduplicatesHtmlLinks() {
        val document = Jsoup.parse(resource("rosstat/catalog.html"), "https://rosstat.gov.ru/statistics/price")
        val parser = RosstatCatalogParser()
        val monthly = parser.links(document, PriceFrequency.MONTHLY)
        val weekly = parser.links(document, PriceFrequency.WEEKLY)

        assertEquals(2, monthly.size)
        assertTrue(monthly.all { it.title.contains("Средние потребительские цены") && it.uri.path.endsWith(".xlsx") })
        assertEquals("/storage/mediabank/159_14-10-2026.html", weekly.single().uri.path)
        assertEquals(LocalDate.of(2026, 10, 12), this.weekly.period(weekly.single().title).referenceDate)
    }

    @Test
    fun testCatalogParserSelectsLatestMonthRegardlessOfSheetOrder() {
        val path = Files.createTempFile("fuel-calc-catalog-months-", ".xlsx")

        try {
            XSSFWorkbook().use { workbook ->
                for ((month, name) in listOf("01(2026)" to "Новый край", "12(2025)" to "Старый край")) {
                    val sheet = workbook.createSheet(month)
                    val header = sheet.createRow(0)
                    header.createCell(0).setCellValue("Код территории")
                    header.createCell(1).setCellValue("Наименование территории")

                    val row = sheet.createRow(1)
                    row.createCell(0).setCellValue(1000000000.0)
                    row.createCell(1).setCellValue(name)
                }

                Files.newOutputStream(path).use(workbook::write)
            }

            assertEquals(listOf(RegionCatalog.Region("01000000000", "Новый край")), RosstatRegionCatalogParser().parse(path))
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun testCatalogRefreshFailureRetainsDatabaseSnapshotWithoutBundledFiles() {
        val store = mock<ImportStore>()
        val http = mock<SourceHttpClient>()
        val saved = listOf(RegionCatalog.Region("01000000000", "Тестовый край"))
        val catalog = RegionCatalog()
        val service = RegionCatalogService(http, properties(), RosstatRegionCatalogParser(), store, catalog, RosstatCatalogParser())
        whenever(store.savedRegions()).thenReturn(saved)
        doThrow(SourceImportException("TLS_FAILED", "Test TLS failure")).whenever(http).beginRun()

        service.restore()

        assertTrue(service.refresh())
        assertEquals(saved, catalog.regions)
        verify(store, never()).synchronizeRegions(any())

        val empty = RegionCatalog()
        val coldStart = RegionCatalogService(http, properties(), RosstatRegionCatalogParser(), store, empty, RosstatCatalogParser())

        assertFalse(coldStart.refresh())
        assertTrue(empty.regions.isEmpty())
    }

    @Test
    fun testCatalogExtractsOnlyRegionsAndPreservesLeadingZeroes() {
        val catalog = fixtureRegions()

        assertEquals(setOf("03000000000", "14000000000"), catalog.map { it.code }.toSet())
        assertTrue(catalog.none { it.code == "14401000000" })
        assertEquals("45000000000", regions.byName("г. Москва")?.code)
    }

    @Test
    fun testCatalogNormalizesAdministrativeNamesWithoutMergingDifferentTerritories() {
        val catalog = RegionCatalog()
        val full = RegionCatalog.Region("01000000000", "Тестовая область (включая автономный округ)")
        val excluding = RegionCatalog.Region("02000000000", "Тестовая область (кроме автономного округа)")
        val alternate = RegionCatalog.Region("03000000000", "Тестовая Республика - Иное название")
        val district = RegionCatalog.Region("04000000000", "Тестовый автономный округ")
        catalog.replace(listOf(full, excluding, alternate, district))

        assertEquals(full, catalog.byName("Тестовая область"))
        assertEquals(excluding, catalog.byName("Тестовая область (кроме автономного округа)"))
        assertEquals(alternate, catalog.byName("Тестовая Республика"))
        assertEquals(district, catalog.byName("в том числе: Тестовый авт. округ"))
        assertFailsWith<IllegalArgumentException> { catalog.replace(listOf(full, full.copy(code = "05000000000"))) }
        assertEquals(full, catalog.byName("Тестовая область"))
    }

    @Test
    fun testWeeklyBulletinUsesExplicitPeriodAndRegionalFuelColumns() {
        val document = Jsoup.parse(resource("rosstat/weekly.html"))
        val publication = weekly.parse(document, "https://rosstat.gov.ru/test.html", LocalDate.of(2026, 9, 9))

        assertEquals(LocalDate.of(2026, 9, 7), publication.period.referenceDate)
        assertEquals(LocalDate.of(2026, 9, 1), publication.period.startDate)
        assertEquals(6, publication.facts.size)
        assertEquals(BigDecimal("80.01"), publication.facts.single { it.regionCode == "03000000000" && it.fuelCode == "DIESEL" }.price)
        assertEquals(setOf("03000000000", "14000000000"), publication.facts.map { it.regionCode }.toSet())
    }

    @Test
    fun testMonthlyWorkbookUsesPublishedDisplayAndExcludesCity() {
        val path = Files.createTempFile("fuel-calc-fixture-", ".xlsx")

        try {
            checkNotNull(javaClass.getResourceAsStream("/sources/rosstat/monthly.xlsx")).use { input ->
                Files.newOutputStream(path).use(input::copyTo)
            }

            val publication = RosstatMonthlyParser(regions, weekly).parse(path, "https://rosstat.gov.ru/test.xlsx", null).single()

            assertEquals(6, publication.facts.size)
            assertEquals(LocalDate.of(2026, 1, 31), publication.period.referenceDate)
            assertEquals(BigDecimal("67.21"), publication.facts.single { it.regionCode == "14000000000" && it.fuelCode == "AI_95" }.price)
            assertTrue(publication.facts.none { it.regionCode == "14401000000" })
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun testMissingPriceIsNotZeroAndUnknownMarkersFail() {
        assertEquals(null, weekly.price("…"))
        assertEquals(BigDecimal.ZERO, weekly.price("0"))
        assertFailsWith<SourceImportException> { weekly.price("н/д?") }
    }

    @Test
    fun testSamplerDeduplicatesAndSelectsFreshestTwenty() {
        val prices = (1..25).map { id -> StationPrice("station-$id", "DIESEL", id.toBigDecimal(), now.minusSeconds(id.toLong())) }
        val samples = RegionalStationSampler().sample("03000000000", prices + prices.first(), now)
        val sample = samples.single()

        assertEquals(20, sample.stationCount)
        assertEquals(BigDecimal("10.500000"), sample.price)
        assertEquals(sample.contentHash, RegionalStationSampler().sample("03000000000", prices.reversed(), now).single().contentHash)
    }

    @Test
    fun testSamplerRejectsStaleFutureAndSmallSamples() {
        val valid = (1..4).map { StationPrice("$it", "DIESEL", BigDecimal.TEN, now.minusSeconds(1)) }
        val stale = StationPrice("stale", "DIESEL", BigDecimal.ONE, now.minusSeconds(8 * 86400))
        val future = StationPrice("future", "DIESEL", BigDecimal.ONE, now.plusSeconds(1))

        assertTrue(RegionalStationSampler().sample("03000000000", valid + stale + future, now).isEmpty())
    }

    @Test
    fun testConflictingStationPriceDoesNotSelectArbitraryVersion() {
        val first = StationPrice("station", "DIESEL", BigDecimal.TEN, now)
        val second = first.copy(price = BigDecimal.ONE)

        assertFailsWith<SourceImportException> { RegionalStationSampler().sample("03000000000", listOf(first, second), now) }
    }

    @Test
    fun testBoundaryExcludesAdjacentRegionAndHole() {
        val boundary = RegionBoundary("region", listOf(listOf(
            listOf(0.0 to 0.0, 4.0 to 0.0, 4.0 to 4.0, 0.0 to 4.0, 0.0 to 0.0),
            listOf(1.0 to 1.0, 2.0 to 1.0, 2.0 to 2.0, 1.0 to 2.0, 1.0 to 1.0)
        )))

        assertTrue(boundary.contains(3.0, 3.0))
        assertFalse(boundary.contains(5.0, 3.0))
        assertFalse(boundary.contains(1.5, 1.5))
    }

    @Test
    fun testHttpRetriesTransientStatusAndDoesNotRetryInvalidPayload() {
        val calls = AtomicInteger()
        val attempts = AtomicInteger()

        stub { index -> if (index == 1) 503 to "unavailable" else 200 to "payload" }.use { server ->
            val client = SourceHttpClient(properties(), clock)
            val body = client.read(server.uri, beforeAttempt = { attempts.incrementAndGet() }) { Files.readString(it) }

            assertEquals("payload", body)
            assertEquals(2, server.calls.get())
            assertEquals(2, attempts.get())
        }

        stub {
            calls.incrementAndGet()

            200 to "invalid"
        }.use { server ->
            val error = assertFailsWith<SourceImportException> {
                SourceHttpClient(properties(), clock).read(server.uri) { throw IllegalArgumentException("Invalid format") }
            }

            assertEquals("INVALID_RESPONSE", error.code)
            assertTrue(error.cause is IllegalArgumentException)
            assertTrue(error.message.orEmpty().contains("Invalid format"))
            assertTrue(error.message.orEmpty().contains(server.uri.host))
            assertEquals(1, calls.get())
        }
    }

    @Test
    fun testTlsHandshakeTimeoutReportsConnectionTimeoutAndPreservesCause() {
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            val properties = properties().also { it.connectTimeout = Duration.ofMillis(200) }
            val uri = URI.create("https://127.0.0.1:${server.localPort}/api/stations")
            val error = assertFailsWith<SourceImportException> {
                SourceHttpClient(properties, clock).read(uri) { Files.readString(it) }
            }

            assertEquals("CONNECT_TIMEOUT", error.code)
            assertTrue(error.message.orEmpty().contains("connectTimeoutMs=200"))
            assertTrue(error.message.orEmpty().contains("TCP/TLS"))
            assertTrue(error.message.orEmpty().contains(uri.toString()))
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is HttpConnectTimeoutException })
        }
    }

    @Test
    fun testHttpRateLimitHasCooldownAndNoImmediateRetry() {
        stub { 429 to "rate limited" }.use { server ->
            val error = assertFailsWith<SourceImportException> {
                SourceHttpClient(properties(), clock).read(server.uri) { Files.readString(it) }
            }

            assertEquals("RATE_LIMITED", error.code)
            assertEquals(now.plusSeconds(120), error.retryAt)
            assertEquals(1, server.calls.get())
        }
    }

    @Test
    fun testHttpResponseSizeIsBounded() {
        val properties = properties().also { it.maxResponseBytes = 32 }

        stub { 200 to "x".repeat(200) }.use { server ->
            val error = assertFailsWith<SourceImportException> {
                SourceHttpClient(properties, clock).read(server.uri) { Files.readString(it) }
            }

            assertEquals("RESPONSE_TOO_LARGE", error.code)
        }
    }

    @Test
    fun testRustataHolidayDateUsesOfficialIntervalOnlyWhenEveryPriceMatches() {
        val sourceDate = LocalDate.of(2026, 2, 23)
        val referenceDate = sourceDate.plusDays(1)
        val calendar = listOf(mapOf<String, Any>(
            "reference_date" to Date.valueOf(referenceDate),
            "start_date" to Date.valueOf(LocalDate.of(2026, 2, 17)),
            "end_date" to Date.valueOf(referenceDate)
        ))
        val official = listOf(mapOf<String, Any>(
            "reference_date" to Date.valueOf(referenceDate), "region_code" to "01000000000",
            "fuel_type_code" to "DIESEL", "price" to BigDecimal("77.40")
        ))

        stub { 200 to resource("rustata/holiday-week.json") }.use { server ->
            val adapter = weeklyRustata(server.uri, calendar, official)
            val result = adapter.fetch(
                adapter.definition(PriceFrequency.WEEKLY), FetchWindow(sourceDate, referenceDate, "BACKFILL")
            ).single()

            assertEquals(referenceDate, result.period.referenceDate)
            assertEquals(LocalDate.of(2026, 2, 17), result.period.startDate)
            assertTrue(result.externalKey.endsWith(sourceDate.toString()))
            assertTrue(result.attribution.contains(sourceDate.toString()))
            assertEquals(BigDecimal("77.4"), result.facts.single().price)
        }

        stub { 200 to resource("rustata/holiday-week.json") }.use { server ->
            val differing = official.map { it + ("price" to BigDecimal("77.41")) }
            val adapter = weeklyRustata(server.uri, calendar, differing)
            val error = assertFailsWith<SourceImportException> {
                adapter.fetch(adapter.definition(PriceFrequency.WEEKLY), FetchWindow(sourceDate, referenceDate, "BACKFILL"))
            }

            assertEquals("UNCONFIRMED_WEEK", error.code)
        }

        stub { 200 to resource("rustata/holiday-week.json") }.use { server ->
            val adapter = weeklyRustata(server.uri, calendar, emptyList())

            assertFailsWith<SourceImportException> {
                adapter.fetch(adapter.definition(PriceFrequency.WEEKLY), FetchWindow(sourceDate, referenceDate, "BACKFILL"))
            }
        }
    }

    @Test
    fun testRustataDoesNotChooseBetweenOverlappingOfficialIntervals() {
        val sourceDate = LocalDate.of(2026, 2, 23)
        val calendar = listOf(1L, 2L).map { days -> mapOf<String, Any>(
            "reference_date" to Date.valueOf(sourceDate.plusDays(days)),
            "start_date" to Date.valueOf(sourceDate.minusDays(6)),
            "end_date" to Date.valueOf(sourceDate.plusDays(days))
        ) }

        stub { 200 to resource("rustata/holiday-week.json") }.use { server ->
            val adapter = weeklyRustata(server.uri, calendar)
            val error = assertFailsWith<SourceImportException> {
                adapter.fetch(adapter.definition(PriceFrequency.WEEKLY), FetchWindow(sourceDate, sourceDate.plusDays(2), "BACKFILL"))
            }

            assertEquals("UNCONFIRMED_WEEK", error.code)
        }
    }

    @Test
    fun testRustataWeeklyUsesConfirmedRosstatIntervals() {
        val sourceDates = listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14))
        val calendar = sourceDates.map { date ->
            mapOf<String, Any>(
                "reference_date" to Date.valueOf(date),
                "start_date" to Date.valueOf(date.minusDays(6)),
                "end_date" to Date.valueOf(date)
            )
        }

        stub { 200 to resource("rustata/weekly.json") }.use { server ->
            val adapter = weeklyRustata(server.uri, calendar)
            val result = adapter.fetch(
                adapter.definition(PriceFrequency.WEEKLY), FetchWindow(sourceDates.first(), sourceDates.last(), "INCREMENTAL")
            )

            assertEquals(sourceDates, result.map { it.period.referenceDate })
            assertEquals(sourceDates.map { it.minusDays(6) }, result.map { it.period.startDate })
            assertEquals(2, result.sumOf { it.facts.size })
        }
    }

    @Test
    fun testRustataWeeklyExplainsMissingDatesWhenRosstatCalendarIsUnavailable() {
        stub { 200 to resource("rustata/weekly.json") }.use { server ->
            val adapter = weeklyRustata(server.uri, emptyList())
            val error = assertFailsWith<SourceImportException> {
                adapter.fetch(
                    adapter.definition(PriceFrequency.WEEKLY),
                    FetchWindow(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14), "BACKFILL")
                )
            }

            assertEquals("UNCONFIRMED_WEEK", error.code)
            assertTrue(error.message.orEmpty().contains("2026-09-07"))
            assertTrue(error.message.orEmpty().contains("2026-09-14"))
            assertTrue(error.message.orEmpty().contains("missingCount=2"))
            assertTrue(error.message.orEmpty().contains("ROSSTAT_WEEKLY_V1"))
        }
    }

    @Test
    fun testHttpFailureIncludesEndpointButDoesNotExposeAuthorizationHeader() {
        stub { 403 to "denied" }.use { server ->
            val token = "private-test-token"
            val error = assertFailsWith<SourceImportException> {
                SourceHttpClient(properties(), clock).read(server.uri, mapOf("Authorization" to "Bearer $token")) {
                    Files.readString(it)
                }
            }

            assertEquals("ACCESS_DENIED", error.code)
            assertTrue(error.message.orEmpty().contains(server.uri.host))
            assertFalse(error.stackTraceToString().contains(token))
        }
    }

    @Test
    fun testRustataKeepsExactDecimalAndNormalizesMonth() {
        stub { 200 to resource("rustata/monthly.json") }.use { server ->
            val properties = properties().also {
                it.rustata.baseUrl = server.uri.toString()
                it.rustata.token = "test-token"
            }
            val weekResolver = RustataWeekResolver(mock<PricePeriodRepository>(), mock<RegionalPriceKeyRepository>())
            val adapter = RustataClient(SourceHttpClient(properties, clock), properties, regions, weekResolver)
            val publication = adapter.fetch(
                adapter.definition(PriceFrequency.MONTHLY),
                FetchWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "INCREMENTAL")
            ).single()

            assertEquals(LocalDate.of(2026, 9, 30), publication.period.referenceDate)
            assertEquals(BigDecimal("74.62000000000001"), publication.facts.single().price)
            assertEquals(LocalDate.of(2026, 10, 9), publication.facts.single().publicationDate)
        }
    }

    @Test
    fun testFailedFinalRustataPageCannotReturnPartialPublication() {
        val firstPage = resource("rustata/monthly.json")
            .replace("\"series_total\": 1", "\"series_total\": 2")
            .replace("\"has_more\": false", "\"has_more\": true")
            .replace("\"next_offset\": null", "\"next_offset\": 1")

        stub { index -> if (index == 1) 200 to firstPage else 403 to "denied" }.use { server ->
            val properties = properties().also {
                it.rustata.baseUrl = server.uri.toString()
                it.rustata.token = "test-token"
                it.rustata.requestInterval = Duration.ZERO
            }
            val weekResolver = RustataWeekResolver(mock<PricePeriodRepository>(), mock<RegionalPriceKeyRepository>())
            val adapter = RustataClient(SourceHttpClient(properties, clock), properties, regions, weekResolver)
            val error = assertFailsWith<SourceImportException> {
                adapter.fetch(adapter.definition(PriceFrequency.MONTHLY), FetchWindow(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "BACKFILL"
                ))
            }

            assertEquals("ACCESS_DENIED", error.code)
            assertEquals(null, error.retryAt)
            assertEquals(2, server.calls.get())
        }
    }

    @Test
    fun testWhereFuelUsesBatchAndDoesNotTreatMissingPricesAsZero() {
        val station = """[{"id":"381","lat":55.745924,"lng":37.604013}]"""

        stub { index -> 200 to if (index == 1) station else resource("wherefuel/no-prices.json") }.use { server ->
            val properties = properties().also {
                it.wherefuel.baseUrl = server.uri.toString()
                it.wherefuel.requestInterval = Duration.ZERO
            }
            val client = WhereFuelClient(SourceHttpClient(properties, clock), properties, regions)
            val boundary = RegionBoundary("45000000000", listOf(listOf(listOf(
                37.0 to 55.0, 38.0 to 55.0, 38.0 to 56.0, 37.0 to 56.0, 37.0 to 55.0
            ))))

            assertTrue(client.fetch(boundary).isEmpty())
            assertEquals(2, server.calls.get())
        }
    }

    @Test
    fun testWhereFuelUsesPriceVerificationTimeInsteadOfStationStatusTime() {
        val station = """[{"id":"381","lat":55.745924,"lng":37.604013}]"""
        val detail = """[{"id":"381","lat":55.745924,"lng":37.604013,"updatedAt":"2020-01-01T00:00:00Z",
            "prices":[{"fuel":"ДТ","price":77.123456789012345678,"checked_at":"2026-10-25 10:00:00+00",
            "created_at":"2020-01-01T00:00:00Z"}]}]"""

        stub { index -> 200 to if (index == 1) station else detail }.use { server ->
            val properties = properties().also {
                it.wherefuel.baseUrl = server.uri.toString()
                it.wherefuel.requestInterval = Duration.ZERO
            }
            val client = WhereFuelClient(SourceHttpClient(properties, clock), properties, regions)
            val boundary = RegionBoundary("45000000000", listOf(listOf(listOf(
                37.0 to 55.0, 38.0 to 55.0, 38.0 to 56.0, 37.0 to 56.0, 37.0 to 55.0
            ))))
            val price = client.fetch(boundary).single()

            assertEquals(BigDecimal("77.123456789012345678"), price.price)
            assertEquals(Instant.parse("2026-10-25T10:00:00Z"), price.observedAt)
            assertEquals("DIESEL", price.fuelCode)
        }
    }

    @Test
    fun testBoundaryReaderLoadsConfiguredGeoJsonPolygon() {
        val geometry = """{"type":"FeatureCollection","features":[{"type":"Feature",
            "properties":{"name":"Москва","code":"45000000000"},
            "geometry":{"type":"MultiPolygon","coordinates":[[[[37,55],[38,55],[38,56],[37,56],[37,55]]]]}}]}"""

        stub { 200 to geometry }.use { server ->
            val properties = properties().also {
                it.wherefuel.boundariesUrl = server.uri.toString()
                it.wherefuel.regionCodes = listOf("45000000000")
            }
            val boundary = WhereFuelClient(SourceHttpClient(properties, clock), properties, regions).boundaries().single()

            assertEquals("45000000000", boundary.regionCode)
            assertTrue(boundary.contains(37.5, 55.5))
            assertFalse(boundary.contains(39.0, 55.5))
        }
    }

    private fun weeklyRustata(
        uri: URI,
        calendar: List<Map<String, Any>>,
        official: List<Map<String, Any>> = emptyList()
    ): RustataClient {
        val properties = properties().also {
            it.rustata.baseUrl = uri.toString()
            it.rustata.token = "test-token"
        }
        val periods = mock<PricePeriodRepository>()
        val keys = mock<RegionalPriceKeyRepository>()
        val series = JsonMapper.builder().build().readTree(resource("rustata/weekly.json")).path("series").first()
        val catalog = RegionCatalog()
        catalog.replace(listOf(RegionCatalog.Region(series.path("geo").asString(), series.path("geo_label").asString())))
        val confirmed = calendar.map { row ->
            PricePeriodEntity(
                periodKey = "WEEKLY:${row.getValue("reference_date")}",
                frequency = PriceFrequency.WEEKLY,
                referenceDate = (row.getValue("reference_date") as Date).toLocalDate(),
                startDate = (row.getValue("start_date") as Date).toLocalDate(),
                endDate = (row.getValue("end_date") as Date).toLocalDate()
            )
        }
        val prices = official.map { row ->
            val period = confirmed.single { it.referenceDate == (row.getValue("reference_date") as Date).toLocalDate() }
            val key = mock<RegionalPriceKeyEntity>()
            val observation = mock<RegionalPriceObservationEntity>()

            whenever(key.period).thenReturn(period)
            whenever(key.region).thenReturn(RegionEntity(code = row.getValue("region_code").toString(), name = "Test", createdAt = now))
            whenever(key.fuelType).thenReturn(
                FuelTypeEntity(code = row.getValue("fuel_type_code").toString(), name = "Test", sortOrder = 1)
            )
            whenever(key.currentObservation).thenReturn(observation)
            whenever(observation.priceRubPerLiter).thenReturn(row.getValue("price") as BigDecimal)

            key
        }

        whenever(periods.findOfficialWeeks(any(), any())).thenReturn(confirmed)
        whenever(keys.findOfficialAtDates(any())).thenReturn(prices)

        return RustataClient(SourceHttpClient(properties, clock), properties, catalog, RustataWeekResolver(periods, keys))
    }

    private fun properties(): ImportProperties =
        ImportProperties().also { it.requestTimeout = Duration.ofSeconds(5) }

    private fun fixtureRegions(): List<RegionCatalog.Region> {
        val path = Files.createTempFile("fuel-calc-region-fixture-", ".xlsx")

        try {
            checkNotNull(javaClass.getResourceAsStream("/sources/rosstat/monthly.xlsx")).use { input ->
                Files.newOutputStream(path).use(input::copyTo)
            }

            return RosstatRegionCatalogParser().parse(path)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/sources/$name")).bufferedReader().use { it.readText() }

    private fun stub(response: (Int) -> Pair<Int, String>): Stub =
        Stub(response)

    private class Stub(
        response: (Int) -> Pair<Int, String>
    ) : AutoCloseable {
        val calls = AtomicInteger()

        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
            server.createContext("/") { exchange ->
                val (status, body) = response(calls.incrementAndGet())
                val bytes = body.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.responseHeaders.set("Retry-After", "120")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()
        }

        val uri: URI = URI.create("http://127.0.0.1:${server.address.port}")

        override fun close() {
            server.stop(0)
        }
    }
}
