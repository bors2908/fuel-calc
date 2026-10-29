package ge.becrin.fuelcalc.sources

import ge.becrin.fuelcalc.sources.benzup.BenzupImportService
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.service.ImportScheduler
import ge.becrin.fuelcalc.sources.service.OfficialImportService
import ge.becrin.fuelcalc.sources.service.RegionCatalogService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportSchedulerTest {
    private val official = mock<OfficialImportService>()

    private val market = mock<BenzupImportService>()

    private val regions = mock<RegionCatalogService>()

    private val scheduler = mock<TaskScheduler>()

    private val properties = ImportProperties()

    private val clock = Clock.fixed(Instant.parse("2026-10-29T18:00:00Z"), ZoneOffset.UTC)

    private val pending = mutableListOf<Runnable>()

    private val importer = ImportScheduler(official, market, regions, scheduler, properties, clock)

    @BeforeEach
    fun prepare() {
        whenever(scheduler.schedule(any<Runnable>(), any<Instant>())).thenAnswer { invocation ->
            pending.add(invocation.getArgument(0))

            mock<ScheduledFuture<Any>>()
        }
    }

    @Test
    fun testStartupLoadsBothSourcesOnlyAfterCatalogRefresh() {
        importer.onReady()

        verify(regions).restore()
        verify(regions, never()).refresh()
        verifyNoInteractions(official, market)
        assertEquals(1, pending.size)

        pending.removeAt(0).run()

        verify(regions).refresh()
        verifyNoInteractions(official, market)
        assertEquals(2, pending.size)

        pending.forEach { it.run() }

        verify(official).fetch()
        verify(market).fetch()
    }

    @Test
    fun testDisabledImportsDoNotScheduleStartupWork() {
        properties.enabled = false

        importer.onReady()

        verifyNoInteractions(regions, scheduler, official, market)
    }

    @Test
    fun testDisabledStartupFetchStillRefreshesCatalogAndAllowsScheduledFetches() {
        properties.fetchOnStartup = false

        importer.onReady()
        pending.removeAt(0).run()

        verify(regions).refresh()
        verifyNoInteractions(official, market)
        assertTrue(pending.isEmpty())

        importer.official()
        importer.market()

        verify(official).fetch()
        verify(market).fetch()
    }

    @Test
    fun testOfficialFailureDoesNotPreventMarketStartup() {
        doThrow(IllegalStateException("unavailable")).whenever(official).fetch()

        importer.onReady()
        pending.removeAt(0).run()
        pending.forEach { it.run() }

        verify(market).fetch()
    }

    @Test
    fun testDisablingImportsBeforeQueuedStartupPreventsFetching() {
        importer.onReady()

        properties.enabled = false
        pending.removeAt(0).run()

        verify(regions, never()).refresh()
        verifyNoInteractions(official, market)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun testMarketStartsWhileOfficialHistoryIsStillLoading() {
        val executor = ThreadPoolTaskScheduler()
        val officialStarted = CountDownLatch(1)
        val releaseOfficial = CountDownLatch(1)
        val marketFinished = CountDownLatch(1)

        executor.poolSize = 3
        executor.initialize()

        doAnswer {
            officialStarted.countDown()
            releaseOfficial.await(5, TimeUnit.SECONDS)

            null
        }.whenever(official).fetch()
        doAnswer {
            marketFinished.countDown()

            null
        }.whenever(market).fetch()

        try {
            ImportScheduler(official, market, regions, executor, properties, Clock.systemUTC()).onReady()

            assertTrue(officialStarted.await(2, TimeUnit.SECONDS), "Official startup task did not begin")
            assertTrue(marketFinished.await(2, TimeUnit.SECONDS), "Market startup waited for official history")
            assertEquals(1L, releaseOfficial.count)
        } finally {
            releaseOfficial.countDown()
            executor.shutdown()
        }
    }
}
