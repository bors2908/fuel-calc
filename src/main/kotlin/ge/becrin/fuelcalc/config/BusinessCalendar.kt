package ge.becrin.fuelcalc.config

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.stereotype.Component

@Component
class BusinessCalendar(
    private val clock: Clock,

    properties: CalendarProperties
) {
    private val zone = ZoneId.of(properties.timeZone)

    fun today(): LocalDate =
        dateAt(clock.instant())

    fun dateAt(instant: Instant): LocalDate =
        instant.atZone(zone).toLocalDate()
}
