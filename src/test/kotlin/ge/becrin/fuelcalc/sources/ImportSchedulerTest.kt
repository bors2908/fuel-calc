package ge.becrin.fuelcalc.sources

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.service.ImportScheduler
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.RegionCatalogService
import ge.becrin.fuelcalc.sources.service.RegionalImportService
import ge.becrin.fuelcalc.sources.wherefuel.WhereFuelImportService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.TaskScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportSchedulerTest {
    private val regional = mock<RegionalImportService>()

    private val whereFuel = mock<WhereFuelImportService>()

    private val catalogService = mock<RegionCatalogService>()

    private val catalog = RegionCatalog()

    private val tasks = ArrayDeque<Runnable>()

    private val properties = ImportProperties()

    private val clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC)

    private val taskScheduler = mock<TaskScheduler>().also { scheduler ->
        whenever(scheduler.schedule(any<Runnable>(), any<Instant>())).thenAnswer { call ->
            tasks.add(call.getArgument(0))

            null
        }
    }

    private val scheduler = ImportScheduler(regional, whereFuel, catalogService, catalog, taskScheduler, properties, clock)

    @Test
    fun testStartupLoadsRosstatCatalogAndPricesBeforeSchedulingOtherSources() {
        doAnswer {
            seedCatalog()

            true
        }.whenever(catalogService).refresh()

        scheduler.onReady()

        assertEquals(1, tasks.size)
        verify(regional, never()).fetch(any(), any())

        tasks.removeFirst().run()

        inOrder(catalogService, regional) {
            verify(catalogService).restore()
            verify(catalogService).refresh()
            verify(regional).fetch("ROSSTAT", true)
        }

        assertEquals(2, tasks.size)
        verify(regional, never()).fetch("RUSTATA", true)
        verify(whereFuel, never()).fetch()

        while (tasks.isNotEmpty()) {
            tasks.removeFirst().run()
        }

        verify(regional).fetch("RUSTATA", true)
        verify(whereFuel).fetch()
        verify(catalogService).refresh()
        verify(regional).fetch("ROSSTAT", true)
    }

    @Test
    fun testMissingCatalogPostponesAllSourcesUntilNextRosstatAttempt() {
        scheduler.onReady()
        tasks.removeFirst().run()
        scheduler.rustata()
        scheduler.whereFuel()

        assertTrue(tasks.isEmpty())
        verify(catalogService).refresh()
        verify(regional, never()).fetch(any(), any())
        verify(whereFuel, never()).fetch()
    }

    @Test
    fun testSavedCatalogAllowsOtherSourcesAfterFailedRosstatRefresh() {
        seedCatalog()

        scheduler.onReady()
        tasks.removeFirst().run()

        while (tasks.isNotEmpty()) {
            tasks.removeFirst().run()
        }

        inOrder(catalogService, regional, whereFuel) {
            verify(catalogService).refresh()
            verify(regional).fetch("ROSSTAT", true)
            verify(regional).fetch("RUSTATA", true)
            verify(whereFuel).fetch()
        }
    }

    @Test
    fun testDisabledStartupFetchLeavesEachProviderToItsCronCallback() {
        properties.fetchOnStartup = false
        seedCatalog()

        scheduler.onReady()

        assertTrue(tasks.isEmpty())
        verify(catalogService, never()).refresh()

        scheduler.rustata()

        verify(catalogService, never()).refresh()
        verify(regional, never()).fetch("ROSSTAT", false)
        verify(regional).fetch("RUSTATA", false)

        scheduler.rosstat()

        verify(catalogService).refresh()
        verify(regional).fetch("ROSSTAT", false)
    }

    @Test
    fun testWhereFuelStartupCanBeDisabledWithoutDisablingItsCron() {
        properties.wherefuel.fetchOnStartup = false
        seedCatalog()

        runStartup()

        verify(regional).fetch("ROSSTAT", true)
        verify(regional).fetch("RUSTATA", true)
        verify(whereFuel, never()).fetch()

        scheduler.whereFuel()

        verify(whereFuel).fetch()
    }

    @Test
    fun testRustataStartupCanBeDisabledWithoutDisablingItsCron() {
        properties.rustata.fetchOnStartup = false
        seedCatalog()

        runStartup()

        verify(regional).fetch("ROSSTAT", true)
        verify(regional, never()).fetch("RUSTATA", true)
        verify(whereFuel).fetch()

        scheduler.rustata()

        verify(regional).fetch("RUSTATA", false)
    }

    @Test
    fun testRosstatStartupCannotBeTriggeredByOtherProviderStartup() {
        properties.rosstat.fetchOnStartup = false
        seedCatalog()

        runStartup()

        verify(catalogService, never()).refresh()
        verify(regional, never()).fetch("ROSSTAT", true)
        verify(regional, never()).fetch("ROSSTAT", false)
        verify(regional).fetch("RUSTATA", true)
        verify(whereFuel).fetch()

        scheduler.rosstat()

        verify(catalogService).refresh()
        verify(regional).fetch("ROSSTAT", false)
    }

    @Test
    fun testAllProviderStartupFlagsCanDisableStartupWithoutGlobalFlag() {
        properties.rosstat.fetchOnStartup = false
        properties.rustata.fetchOnStartup = false
        properties.wherefuel.fetchOnStartup = false

        scheduler.onReady()

        assertTrue(tasks.isEmpty())
        verify(catalogService).restore()
        verify(catalogService, never()).refresh()
        verify(regional, never()).fetch(any(), any())
        verify(whereFuel, never()).fetch()
    }

    @Test
    fun testColdDatabaseWaitsForRosstatCronWhenRosstatStartupIsDisabled() {
        properties.rosstat.fetchOnStartup = false

        runStartup()

        verify(catalogService, never()).refresh()
        verify(regional, never()).fetch(any(), any())
        verify(whereFuel, never()).fetch()

        doAnswer {
            seedCatalog()

            true
        }.whenever(catalogService).refresh()

        scheduler.rosstat()
        scheduler.rustata()
        scheduler.whereFuel()

        inOrder(regional, whereFuel) {
            verify(regional).fetch("ROSSTAT", false)
            verify(regional).fetch("RUSTATA", false)
            verify(whereFuel).fetch()
        }
    }

    @Test
    fun testDisabledProviderIsNotStartedByItsCronOrStartupFlag() {
        properties.wherefuel.enabled = false
        seedCatalog()

        runStartup()
        scheduler.whereFuel()

        verify(whereFuel, never()).fetch()
    }

    private fun runStartup() {
        scheduler.onReady()

        while (tasks.isNotEmpty()) {
            tasks.removeFirst().run()
        }
    }

    private fun seedCatalog() {
        catalog.replace(listOf(RegionCatalog.Region("01000000000", "Тестовый край")))
    }
}
