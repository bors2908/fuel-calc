package ge.becrin.fuelcalc.sources

import com.sun.net.httpserver.HttpServer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import ge.becrin.fuelcalc.sources.config.ImportProperties
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import org.springframework.scheduling.config.CronTask
import org.springframework.scheduling.support.SimpleTriggerContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "imports.enabled=true",
        "imports.fetch-on-startup=true",
        "imports.rosstat.fetch-on-startup=true",
        "imports.time-zone=Europe/Moscow",
        "imports.rosstat.cron=0 0 */6 * * *",
        "imports.rustata.cron=0 30 */6 * * *",
        "imports.wherefuel.cron=0 45 * * * *",
        "imports.rustata.enabled=false",
        "imports.wherefuel.enabled=false",
        "imports.history-from=2026-09-01",
        "imports.history-windows-per-run=1"
    ]
)
@Testcontainers
class StartupImportTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var scheduled: ScheduledAnnotationBeanPostProcessor

    @Autowired
    private lateinit var properties: ImportProperties

    @Test
    fun testProviderSchedulesAreRegisteredAsCronWithConfiguredTimeZone() {
        val tasks = scheduled.scheduledTasks.map { it.task }

        assertEquals(3, tasks.size)

        val cronTasks = tasks.map { it as CronTask }.associateBy { it.expression }
        val zone = ZoneId.of(properties.timeZone)
        val clock = Clock.fixed(Instant.parse("2026-10-26T20:59:59Z"), zone)
        val context = SimpleTriggerContext(clock)

        assertEquals(
            Instant.parse("2026-10-26T21:00:00Z"),
            cronTasks.getValue(properties.rosstat.cron).trigger.nextExecution(context)
        )
        assertEquals(
            Instant.parse("2026-10-26T21:30:00Z"),
            cronTasks.getValue(properties.rustata.cron).trigger.nextExecution(context)
        )
        assertEquals(
            Instant.parse("2026-10-26T21:45:00Z"),
            cronTasks.getValue(properties.wherefuel.cron).trigger.nextExecution(context)
        )
    }

    @Test
    fun testStartupFetchPopulatesPostgresAndCompletesProgressWithoutExternalServices() {
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertEquals(6L, jdbc.queryForObject("SELECT COUNT(*) FROM regional_price_observations", Long::class.java))
            assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM import_runs WHERE status = 'SUCCEEDED'", Long::class.java))
        }

        assertEquals(6L, jdbc.queryForObject(
            "SELECT COUNT(*) FROM regional_price_keys WHERE current_observation_id IS NOT NULL AND provider_code = 'ROSSTAT'",
            Long::class.java
        ))
        assertEquals(2L, jdbc.queryForObject(
            "SELECT COUNT(*) FROM source_fetch_states WHERE backfill_completed_at IS NOT NULL", Long::class.java
        ))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM import_runs WHERE status = 'FAILED'", Long::class.java))
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM regions", Long::class.java))
        assertEquals(listOf("/monthly", "/prices_2026.xlsx"), requests.take(2))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        private val requests = CopyOnWriteArrayList<String>()

        private val source = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
            server.createContext("/") { exchange ->
                val path = exchange.requestURI.path
                requests.add(path)

                val bytes =
                    when (path) {
                        "/weekly" -> (
                            "<div class='document-list__item'><div class='document-list__item-title'>" +
                                "О потребительских ценах на нефтепродукты с 1 по 7 сентября 2026 года</div>" +
                                "<a href='/138_09-09-2026.html'>HTML</a></div>"
                            ).toByteArray()
                        "/monthly" -> (
                            "<div class='document-list__item'><div class='document-list__item-title'>" +
                                "Средние потребительские цены на отдельные виды товаров и услуг " +
                                "по субъектам Российской Федерации (в 2026 г.)</div>" +
                                "<a href='/prices_2026.xlsx'>XLSX</a></div>"
                            ).toByteArray()
                        "/prices_2026.xlsx" -> resource("monthly.xlsx")
                        else -> resource("weekly.html")
                    }

                exchange.responseHeaders.set("Content-Type", "application/octet-stream")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun sourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("imports.rosstat.weekly-catalog") { "http://127.0.0.1:${source.address.port}/weekly" }
            registry.add("imports.rosstat.monthly-catalog") { "http://127.0.0.1:${source.address.port}/monthly" }
        }

        @JvmStatic
        @AfterAll
        fun stopSource() {
            source.stop(0)
        }

        private fun resource(name: String): ByteArray =
            checkNotNull(StartupImportTest::class.java.getResourceAsStream("/sources/rosstat/$name")).use { it.readBytes() }
    }
}
