package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.config.ImportProperties
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@Component
class SourceHttpClient(
    private val properties: ImportProperties,

    private val clock: Clock
) {
    private class RosstatTrustManager : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> =
            emptyArray()

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
        }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
        }
    }

    private val client = createClient(HttpClient.newBuilder())

    private val rosstatHosts = setOf(
        URI.create(properties.rosstat.weeklyCatalog).host, URI.create(properties.rosstat.monthlyCatalog).host
    )

    private val rosstatClient by lazy {
        // TODO: Restore certificate validation after fixing the Rosstat certificate chain; keep other sources strict.
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf(RosstatTrustManager()), null)

        log.warn("Rosstat TLS certificate validation disabled [hosts={}]", rosstatHosts)

        createClient(HttpClient.newBuilder().sslContext(ssl))
    }

    private val runDeadline = ThreadLocal<Instant>()

    fun beginRun() {
        runDeadline.set(clock.instant().plus(properties.maxRunDuration))
    }

    fun <T> read(
        uri: URI,
        headers: Map<String, String> = emptyMap(),
        beforeAttempt: () -> Unit = {},
        reader: (Path) -> T
    ): T {
        val path = Files.createTempFile("fuel-calc-source-", ".download")
        val endpoint = URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()

        try {
            for (attempt in 1..3) {
                if (runDeadline.get()?.isBefore(clock.instant()) == true) {
                    throw SourceImportException("RUN_BUDGET", "Source run duration budget exhausted")
                }

                beforeAttempt()

                try {
                    download(uri, headers, path)

                    break
                } catch (error: SourceImportException) {
                    if (error.code != "HTTP_TRANSIENT" || attempt == 3) {
                        throw SourceImportException(error.code, "${error.message} [endpoint=$endpoint]", error.retryAt, error)
                    }

                    log.warn(
                        "Source HTTP request will be retried [endpoint={}, attempt={}, errorCode={}, message={}]",
                        endpoint, attempt, error.code, error.message
                    )
                } catch (error: Exception) {
                    val failure = transportFailure(error, endpoint)

                    if (failure.code == "TLS_FAILED" || attempt == 3 || Thread.currentThread().isInterrupted) {
                        throw failure
                    }

                    val cause = generateSequence<Throwable>(error) { it.cause }.last()

                    log.warn(
                        "Source request will be retried [endpoint={}, attempt={}, maxAttempts=3, errorCode={}, " +
                            "connectTimeoutMs={}, causeType={}, reason={}]",
                        endpoint, attempt, failure.code, properties.connectTimeout.toMillis(),
                        cause.javaClass.simpleName, cause.message
                    )
                    log.debug("Source request attempt failed [endpoint={}, attempt={}]", endpoint, attempt, error)
                }

                Thread.sleep(if (attempt == 1) 1000 else 3000)
            }

            try {
                return reader(path)
            } catch (error: Exception) {
                throw SourceImportException(
                    (error as? SourceImportException)?.code ?: "INVALID_RESPONSE",
                    "Source response parsing failed [endpoint=$endpoint, reason=${error.message}]",
                    (error as? SourceImportException)?.retryAt,
                    error
                )
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun download(
        uri: URI,
        headers: Map<String, String>,
        path: Path
    ) {
        require(uri.scheme in setOf("https", "http"))
        val size = AtomicLong()
        val selectedClient =
            if (properties.rosstat.ignoreTlsErrors && uri.scheme == "https" && uri.host in rosstatHosts) rosstatClient else client
        val started = System.nanoTime()

        log.debug("Source HTTP request started [host={}, path={}]", uri.host, uri.path)

        val buffers = selectedClient.get().uri(uri).headers { h -> headers.forEach { (key, value) -> h.set(key, value) } }
            .exchangeToFlux { response ->
                val status = response.statusCode().value()

                if (status !in 200..299) {
                    val retryAt =
                        if (status == 429) {
                            parseRetryAfter(response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                        } else {
                            null
                        }
                    val code =
                        when (status) {
                            429 -> "RATE_LIMITED"
                            401, 403 -> "ACCESS_DENIED"
                            502, 503, 504 -> "HTTP_TRANSIENT"
                            else -> "HTTP_ERROR"
                        }

                    response.releaseBody().thenMany(
                        Flux.error<DataBuffer>(SourceImportException(code, "Source HTTP status $status", retryAt))
                    )
                } else {
                    response.bodyToFlux(DataBuffer::class.java)
                }
            }
            .handle<DataBuffer> { buffer, sink ->
                if (size.addAndGet(buffer.readableByteCount().toLong()) > properties.maxResponseBytes) {
                    DataBufferUtils.release(buffer)
                    sink.error(SourceImportException("RESPONSE_TOO_LARGE", "Source response exceeds configured limit"))
                } else {
                    sink.next(buffer)
                }
            }
            .doOnDiscard(DataBuffer::class.java, DataBufferUtils::release)

        val timeout =
            if (uri.host in rosstatHosts) properties.rosstat.requestTimeout else properties.requestTimeout
        val remaining = runDeadline.get()?.let { Duration.between(clock.instant(), it) } ?: timeout

        if (remaining.isNegative || remaining.isZero) {
            throw SourceImportException("RUN_BUDGET", "Source run duration budget exhausted")
        }

        DataBufferUtils.write(buffers, path).block(minOf(timeout, remaining))

        if (size.get() == 0L) {
            throw SourceImportException("EMPTY_RESPONSE", "Source returned an empty response")
        }

        log.debug(
            "Source HTTP request completed [host={}, path={}, bytes={}, durationMs={}]",
            uri.host, uri.path, size.get(), Duration.ofNanos(System.nanoTime() - started).toMillis()
        )
    }

    private fun createClient(builder: HttpClient.Builder): WebClient =
        WebClient.builder()
            .clientConnector(JdkClientHttpConnector(
                builder.connectTimeout(properties.connectTimeout).version(HttpClient.Version.HTTP_1_1).build()
            ))
            .defaultHeader(HttpHeaders.USER_AGENT, "fuel-calc/0.1 (regional price research)")
            .build()

    private fun transportFailure(
        error: Exception,
        endpoint: String
    ): SourceImportException {
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val (code, message) =
            when {
                causes.any { it is HttpConnectTimeoutException } -> {
                    "CONNECT_TIMEOUT" to "Source connection establishment (TCP/TLS) timed out " +
                        "[connectTimeoutMs=${properties.connectTimeout.toMillis()}]"
                }
                causes.any { it is SSLException } -> "TLS_FAILED" to "Source TLS verification or handshake failed"
                causes.any { it is HttpTimeoutException || it is TimeoutException } -> {
                    "REQUEST_TIMEOUT" to "Source response exceeded the request timeout or remaining run budget"
                }
                else -> "FETCH_FAILED" to "Source download failed"
            }

        return SourceImportException(code, "$message [endpoint=$endpoint]", cause = error)
    }

    private fun parseRetryAfter(value: String?): Instant {
        val now = clock.instant()

        return value?.toLongOrNull()?.let { now.plusSeconds(it.coerceIn(1, 86400)) }
            ?: value?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() }
            ?: now.plusSeconds(3600)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SourceHttpClient::class.java)
    }
}
