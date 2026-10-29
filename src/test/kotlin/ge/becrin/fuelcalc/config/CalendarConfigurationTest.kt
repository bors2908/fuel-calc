package ge.becrin.fuelcalc.config

import ge.becrin.fuelcalc.sources.config.ImportProperties
import jakarta.validation.Validation
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CalendarConfigurationTest {
    @Test
    fun testBusinessDateUsesConfiguredZoneAtMidnight() {
        val instant = Instant.parse("2026-10-29T22:30:00Z")
        val clock = Clock.fixed(instant, ZoneOffset.UTC)
        val moscow = BusinessCalendar(clock, CalendarProperties())
        val utc = BusinessCalendar(clock, CalendarProperties().also { it.timeZone = "UTC" })

        assertEquals(LocalDate.of(2026, 10, 30), moscow.today())
        assertEquals(moscow.today(), moscow.dateAt(instant))
        assertEquals(LocalDate.of(2026, 10, 29), utc.today())
    }

    @Test
    fun testInvalidImportLimitsAndTimeZoneAreRejected() {
        Validation.buildDefaultValidatorFactory().use { factory ->
            val validator = factory.validator
            val properties = ImportProperties()

            assertTrue(validator.validate(properties).isEmpty())
            assertTrue(validator.validate(CalendarProperties()).isEmpty())

            properties.connectTimeout = Duration.ZERO
            properties.maxResponseBytes = 0
            properties.rosstat.requestTimeout = Duration.ofSeconds(-1)
            properties.rustata.requestInterval = Duration.ofSeconds(-1)
            properties.rustata.pageSize = 0
            properties.rustata.maxPages = 0

            val paths = validator.validate(properties).map { it.propertyPath.toString() }.toSet()

            assertTrue(paths.containsAll(setOf(
                "validDurations", "maxResponseBytes", "rosstat.validTimeout",
                "rustata.validInterval", "rustata.pageSize", "rustata.maxPages"
            )))
            assertTrue(validator.validate(CalendarProperties().also { it.timeZone = "Unknown/Zone" }).isNotEmpty())
        }
    }
}
