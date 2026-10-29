package ge.becrin.fuelcalc.sources

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.security.KeyStore
import java.time.Clock
import java.time.Duration
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SourceTlsTest {
    @Test
    fun testConfiguredRosstatHostCanLoadWithUntrustedCertificate() {
        TlsSource().use { source ->
            val properties = properties(source.uri, true)
            val result = SourceHttpClient(properties, Clock.systemUTC()).read(source.uri) { Files.readString(it) }

            assertEquals("test payload", result)
        }
    }

    @Test
    fun testOtherSourcesKeepTlsValidationWhenRosstatBypassIsEnabled() {
        TlsSource().use { source ->
            val properties = ImportProperties().also { it.requestTimeout = Duration.ofSeconds(5) }
            val error = assertFailsWith<SourceImportException> {
                SourceHttpClient(properties, Clock.systemUTC()).read(source.uri) { Files.readString(it) }
            }

            assertTlsFailureWithRequestAndCause(error, source.uri)
        }
    }

    @Test
    fun testRosstatCertificateValidationCanBeReenabled() {
        TlsSource().use { source ->
            val error = assertFailsWith<SourceImportException> {
                SourceHttpClient(properties(source.uri, false), Clock.systemUTC()).read(source.uri) { Files.readString(it) }
            }

            assertTlsFailureWithRequestAndCause(error, source.uri)
        }
    }

    private fun properties(
        uri: URI,
        ignoreTlsErrors: Boolean
    ): ImportProperties =
        ImportProperties().also {
            it.requestTimeout = Duration.ofSeconds(5)
            it.rosstat.baseUrl = uri.toString()
            it.rosstat.ignoreTlsErrors = ignoreTlsErrors
        }

    private fun assertTlsFailureWithRequestAndCause(
        error: SourceImportException,
        uri: URI
    ) {
        assertEquals("TLS_FAILED", error.code)
        assertTrue(error.message.orEmpty().contains(uri.toString()))
        assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it is SSLException })
    }

    private class TlsSource : AutoCloseable {
        private val server: HttpsServer

        val uri: URI

        init {
            val store = KeyStore.getInstance("PKCS12")
            checkNotNull(javaClass.getResourceAsStream("/sources/http/localhost.p12")).use { store.load(it, "changeit".toCharArray()) }

            val manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            manager.init(store, "changeit".toCharArray())

            val context = SSLContext.getInstance("TLS")
            context.init(manager.keyManagers, null, null)

            server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.httpsConfigurator = HttpsConfigurator(context)
            server.createContext("/") { exchange ->
                val bytes = "test payload".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            server.start()

            uri = URI.create("https://localhost:${server.address.port}/")
        }

        override fun close() {
            server.stop(0)
        }
    }
}
