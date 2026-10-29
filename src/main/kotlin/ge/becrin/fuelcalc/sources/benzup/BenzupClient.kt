package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceJson
import java.net.URI
import org.springframework.stereotype.Component

@Component
class BenzupClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val parser: BenzupResponseParser,

    private val json: SourceJson
) {
    fun fetch(): List<BenzupPrice> {
        val headers = properties.benzup.token.takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

        return http.read(URI.create(properties.benzup.endpoint), headers) { path -> parser.parse(json.mapper.readTree(path.toFile())) }
    }
}
