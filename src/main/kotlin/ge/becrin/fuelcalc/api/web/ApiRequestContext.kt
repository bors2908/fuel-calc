package ge.becrin.fuelcalc.api.web

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Component
class ApiRequestContext(private val clock: Clock) {
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

        return RequestInfo(UUID.randomUUID().toString(), now, now.atZone(ZoneId.of("Europe/Moscow")).toLocalDate())
    }
}
