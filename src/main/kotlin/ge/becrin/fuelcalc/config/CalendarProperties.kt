package ge.becrin.fuelcalc.config

import jakarta.validation.constraints.AssertTrue
import java.time.ZoneId
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties("calendar")
class CalendarProperties {
    var timeZone: String = "Europe/Moscow"

    @get:AssertTrue(message = "calendar.time-zone must be a valid zone ID")
    val validTimeZone: Boolean
        get() =
            runCatching { ZoneId.of(timeZone) }.isSuccess
}
