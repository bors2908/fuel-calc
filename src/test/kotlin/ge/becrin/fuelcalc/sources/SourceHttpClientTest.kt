package ge.becrin.fuelcalc.sources

import com.sun.net.httpserver.HttpServer
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import ge.becrin.fuelcalc.sources.service.SourceJson
import ge.becrin.fuelcalc.sources.service.SourceTransport
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SourceHttpClientTest {
    private class MutableClock : Clock() {
        var now: Instant = Instant.parse("2026-10-29T12:00:00Z")

        override fun getZone(): ZoneId =
            ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock =
            fixed(now, zone)

        override fun instant(): Instant =
            now
    }

    private class Source(private val status: Int = 200) : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        val requests = AtomicInteger()

        val uri: URI
            get() =
                URI.create("http://127.0.0.1:${server.address.port}/data")

        init {
            server.createContext("/data") { exchange ->
                requests.incrementAndGet()

                val body = "payload".toByteArray()
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
        }
    }

    @Test
    fun testExpiredNestedRunCannotResetBudgetAndDeadlineIsClearedAfterFailure() {
        Source().use { source ->
            val clock = MutableClock()
            val properties = ImportProperties().also { it.maxRunDuration = Duration.ofSeconds(1) }
            val client = client(properties, clock)
            val error = assertFailsWith<SourceImportException> {
                client.run {
                    clock.now = clock.now.plusSeconds(2)

                    client.run { client.read(source.uri) { Files.readString(it) } }
                }
            }

            assertEquals("RUN_BUDGET", error.code)
            assertEquals(0, source.requests.get())
            assertEquals("payload", client.run { client.read(source.uri) { Files.readString(it) } })
            assertEquals(1, source.requests.get())
        }
    }

    @Test
    fun testInterruptedAttemptPreservesFlagAndDoesNotPoisonTheNextRun() {
        Source().use { source ->
            val client = client(ImportProperties(), Clock.systemUTC())

            try {
                assertFailsWith<InterruptedException> {
                    client.run {
                        client.read(source.uri, beforeAttempt = { throw InterruptedException("stopped") }) { Files.readString(it) }
                    }
                }

                assertTrue(Thread.currentThread().isInterrupted)
                assertEquals(0, source.requests.get())
            } finally {
                Thread.interrupted()
            }

            assertEquals("payload", client.run { client.read(source.uri) { Files.readString(it) } })
        }
    }

    @Test
    fun testRateLimitedSourceIsNotRetriedImmediately() {
        Source(429).use { source ->
            val client = client(ImportProperties(), Clock.systemUTC())
            val error = assertFailsWith<SourceImportException> { client.read(source.uri) { Files.readString(it) } }

            assertEquals("RATE_LIMITED", error.code)
            assertEquals(1, source.requests.get())
        }
    }

    @Test
    fun testResponseLimitPreventsParsingOversizedData() {
        Source().use { source ->
            val properties = ImportProperties().also { it.maxResponseBytes = 2 }
            val client = client(properties, Clock.systemUTC())
            var parsed = false
            val error = assertFailsWith<SourceImportException> {
                client.read(source.uri) {
                    parsed = true
                }
            }

            assertEquals("RESPONSE_TOO_LARGE", error.code)
            assertFalse(parsed)
        }
    }

    private fun client(
        properties: ImportProperties,
        clock: Clock
    ): SourceHttpClient =
        SourceHttpClient(SourceTransport(properties), properties, clock, SourceJson())
}
