package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.sources.config.ImportProperties
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.nio.file.Path
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux

@Component
class SourceTransport(private val properties: ImportProperties) {
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

    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    private val client = createClient(HttpClient.newBuilder())

    private val rosstatHosts = setOf(
        URI.create(properties.rosstat.baseUrl).host, URI.create(properties.regions.okatoEndpoint).host,
        URI.create(properties.regions.selectionEndpoint).host
    )

    private val rosstatClient by lazy {
        // TODO: Restore certificate validation after fixing the Rosstat certificate chain; keep other sources strict.
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf(RosstatTrustManager()), null)

        log.warn("Rosstat TLS certificate validation disabled [hosts={}]", rosstatHosts)

        createClient(HttpClient.newBuilder().sslContext(ssl))
    }

    fun download(
        uri: URI,
        headers: Map<String, String>,
        path: Path,
        form: Map<String, String>?,
        remaining: Duration?
    ) {
        require(uri.scheme in setOf("https", "http"))
        val size = AtomicLong()
        val selectedClient =
            if (properties.rosstat.ignoreTlsErrors && uri.scheme == "https" && uri.host in rosstatHosts) rosstatClient else client
        val started = System.nanoTime()

        log.debug("Source HTTP request started [host={}, path={}]", uri.host, uri.path)

        val request =
            if (form == null) {
                selectedClient.get().uri(uri).headers { h -> headers.forEach { (key, value) -> h.set(key, value) } }
            } else {
                val values = LinkedMultiValueMap<String, String>()

                form.forEach { (key, value) -> values.add(key, value) }

                selectedClient.post().uri(uri).headers { h ->
                    headers.forEach { (key, value) -> h.set(key, value) }
                    h.set(HttpHeaders.REFERER, uri.toString())
                    cookies.cookieStore.get(uri).singleOrNull { it.name == "csrftoken" }?.let { cookie ->
                        h.set("X-CSRFToken", cookie.value)
                    }
                }
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(BodyInserters.fromFormData(values))
            }
        val buffers = request
            .exchangeToFlux { response ->
                val status = response.statusCode().value()

                if (status !in 200..299) {
                    val code =
                        when (status) {
                            429 -> "RATE_LIMITED"
                            401, 403 -> "ACCESS_DENIED"
                            502, 503, 504 -> "HTTP_TRANSIENT"
                            else -> "HTTP_ERROR"
                        }

                    response.releaseBody().thenMany(
                        Flux.error<DataBuffer>(SourceImportException(code, "Source HTTP status $status"))
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
        DataBufferUtils.write(buffers, path).block(minOf(timeout, remaining ?: timeout))

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
                builder.cookieHandler(cookies).connectTimeout(properties.connectTimeout).version(HttpClient.Version.HTTP_1_1).build()
            ))
            .defaultHeader(HttpHeaders.USER_AGENT, "fuel-calc/0.1 (regional price research)")
            .build()

    companion object {
        private val log = LoggerFactory.getLogger(SourceTransport::class.java)
    }
}
