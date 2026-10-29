package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.config.ImportProperties
import java.net.URI
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class SourceHttpClient(
    private val transport: SourceTransport,

    private val properties: ImportProperties,

    private val clock: Clock,

    private val json: SourceJson
) {
    private val runDeadline = ThreadLocal<Instant>()

    fun <T> run(action: () -> T): T {
        val previous = runDeadline.get()
        runDeadline.set(previous ?: clock.instant().plus(properties.maxRunDuration))

        try {
            return action()
        } finally {
            if (previous == null) {
                runDeadline.remove()
            } else {
                runDeadline.set(previous)
            }
        }
    }

    fun <T> read(
        uri: URI,
        headers: Map<String, String> = emptyMap(),
        beforeAttempt: () -> Unit = {},
        form: Map<String, String>? = null,
        reader: (Path) -> T
    ): T {
        val path = Files.createTempFile("fuel-calc-source-", ".download")
        val endpoint = URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()

        try {
            for (attempt in 1..MAX_ATTEMPTS) {
                remaining()

                beforeAttempt()

                try {
                    transport.download(uri, headers, path, form, remaining())

                    break
                } catch (error: SourceImportException) {
                    if (error.code != "HTTP_TRANSIENT" || attempt == MAX_ATTEMPTS) {
                        throw SourceImportException(error.code, "${error.message} [endpoint=$endpoint]", cause = error)
                    }

                    log.warn(
                        "Source HTTP request will be retried [endpoint={}, attempt={}, errorCode={}, message={}]",
                        endpoint, attempt, error.code, error.message
                    )
                } catch (error: Exception) {
                    interrupted(error)

                    val failure = transportFailure(error, endpoint)

                    if (failure.code == "TLS_FAILED" || attempt == MAX_ATTEMPTS || Thread.currentThread().isInterrupted) {
                        throw failure
                    }

                    val cause = generateSequence<Throwable>(error) { it.cause }.last()

                    log.warn(
                        "Source request will be retried [endpoint={}, attempt={}, maxAttempts=$MAX_ATTEMPTS, errorCode={}, " +
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
                interrupted(error)

                throw SourceImportException(
                    (error as? SourceImportException)?.code ?: "INVALID_RESPONSE",
                    "Source response parsing failed [endpoint=$endpoint, reason=${error.message}]",
                    cause = error
                )
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()

            throw error
        } finally {
            Files.deleteIfExists(path)
        }
    }

    fun json(
        uri: URI,
        form: Map<String, String>? = null
    ): JsonNode =
        read(uri, form = form) { json.mapper.readTree(it.toFile()) }

    private fun remaining(): Duration? {
        val duration = runDeadline.get()?.let { Duration.between(clock.instant(), it) }

        if (duration != null && (duration.isNegative || duration.isZero)) {
            throw SourceImportException("RUN_BUDGET", "Source run duration budget exhausted")
        }

        return duration
    }

    private fun interrupted(error: Exception) {
        val interruption = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<InterruptedException>().firstOrNull()

        if (interruption != null) {
            Thread.currentThread().interrupt()

            throw interruption
        }
    }

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

    companion object {
        private const val MAX_ATTEMPTS = 3

        private val log = LoggerFactory.getLogger(SourceHttpClient::class.java)
    }
}
