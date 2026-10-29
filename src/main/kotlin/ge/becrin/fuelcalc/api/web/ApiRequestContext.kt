package ge.becrin.fuelcalc.api.web

import ge.becrin.fuelcalc.config.BusinessCalendar
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class ApiRequestContext(
    private val clock: Clock,

    private val calendar: BusinessCalendar
) {
    data class RequestInfo(
        val requestId: String,

        val generatedAt: Instant,

        val serverDate: LocalDate
    )

    private val local = ThreadLocal<RequestInfo>()

    fun begin(): RequestInfo {
        val info = create()
        local.set(info)

        return info
    }

    fun current(): RequestInfo =
        local.get() ?: create()

    fun clear() {
        local.remove()
    }

    private fun create(): RequestInfo {
        val now = clock.instant()

        return RequestInfo(UUID.randomUUID().toString(), now, calendar.dateAt(now))
    }
}
