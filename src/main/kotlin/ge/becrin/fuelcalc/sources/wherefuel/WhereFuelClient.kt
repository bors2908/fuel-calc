package ge.becrin.fuelcalc.sources.wherefuel

import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceHttpClient
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Duration
import java.time.Instant

@Component
class WhereFuelClient(
    private val http: SourceHttpClient,

    private val properties: ImportProperties,

    private val regions: RegionCatalog
) {
    private val json = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()

    private var requests: Int = 0

    private var lastRequest: Long = 0

    fun beginRun() {
        http.beginRun()
        requests = 0
    }

    fun boundaries(): List<RegionBoundary> {
        val root = http.read(URI.create(properties.wherefuel.boundariesUrl)) { json.readTree(it.toFile()) }
        require(root.path("type").asString() == "FeatureCollection") { "Expected GeoJSON FeatureCollection" }
        val result = root.path("features").toList().mapNotNull { feature ->
            val name = feature.path("properties").path("name").asString("")
            val code = feature.path("properties").path("code").asString("")
            val region = regions.byCode(code) ?: regions.byName(name) ?: regions.byName("Республика $name")

            if (region == null || properties.wherefuel.regionCodes.isNotEmpty() && region.code !in properties.wherefuel.regionCodes) {
                null
            } else {
                val geometry = feature.path("geometry")
                val coordinates = geometry.path("coordinates")
                val polygons =
                    when (geometry.path("type").asString()) {
                        "Polygon" -> listOf(coordinates)
                        "MultiPolygon" -> coordinates.toList()
                        else -> throw SourceImportException("INVALID_GEOMETRY", "Unsupported region geometry")
                    }
                val rings = polygons.map { polygon -> polygon.toList().map { ring -> ring.toList().map { point ->
                    val x = point[0].asDouble()
                    val y = point[1].asDouble()
                    require(x.isFinite() && y.isFinite() && x in -180.0..180.0 && y in -90.0..90.0)

                    x to y
                } } }
                require(rings.all { polygon -> polygon.isNotEmpty() && polygon.all { it.size >= 4 && it.first() == it.last() } })

                RegionBoundary(region.code, rings)
            }
        }.distinctBy { it.regionCode }
        require(result.isNotEmpty()) { "No verified region boundaries available" }
        require(properties.wherefuel.regionCodes.all { code -> result.any { it.regionCode == code } }) { "Missing configured boundary" }

        return result
    }

    fun fetch(boundary: RegionBoundary): List<StationPrice> {
        val stations = mutableMapOf<String, JsonNode>()
        val bounds = boundary.bounds
        require(bounds[2] - bounds[0] <= 180) { "Antimeridian boundary requires split geometry" }
        collect(bounds, 0, stations)
        val selected = stations.values.filter { station ->
            boundary.contains(station.path("lng").asDouble(), station.path("lat").asDouble())
        }
        val result = mutableListOf<StationPrice>()

        for (chunk in selected.chunked(50)) {
            val ids = chunk.map { it.path("id").asString() }
            val uri = UriComponentsBuilder.fromUriString(properties.wherefuel.baseUrl).path("/api/stations/batch")
                .queryParam("ids", ids.joinToString(",")).build().toUri()
            val response = request(uri)
            require(response.isArray && response.size() == ids.size) { "Incomplete station price batch" }
            require(response.toList().map { it.path("id").asString() }.toSet() == ids.toSet()) { "Station price batch IDs mismatch" }

            for (station in response) {
                require(boundary.contains(station.path("lng").asDouble(), station.path("lat").asDouble()))
                val prices = station.path("prices")
                require(prices.isArray) { "Missing station prices array" }

                for (price in prices) {
                    val fuel = FUELS[price.path("fuel").asString("")] ?: continue
                    val value = price.path("price").asString("").toBigDecimalOrNull() ?: continue
                    val observedAt = sequenceOf("checked_at", "created_at").mapNotNull { field ->
                        price.path(field).asString("").takeIf { it.isNotBlank() }?.let { value ->
                            val normalized = value.replace(' ', 'T').replace(Regex("([+-]\\d{2})$"), "$1:00")

                            runCatching { Instant.parse(normalized) }.getOrNull()
                        }
                    }.firstOrNull() ?: continue

                    result.add(StationPrice(station.path("id").asString(), fuel, value, observedAt))
                }
            }
        }

        return result
    }

    private fun collect(
        bounds: List<Double>,
        depth: Int,
        stations: MutableMap<String, JsonNode>
    ) {
        val uri = UriComponentsBuilder.fromUriString(properties.wherefuel.baseUrl).path("/api/stations")
            .queryParam("bbox", bounds.joinToString(",")).queryParam("limit", properties.wherefuel.responseLimit).build().toUri()
        val response = request(uri)
        require(response.isArray) { "Expected station array" }

        if (response.size() >= properties.wherefuel.responseLimit) {
            if (depth >= 10) {
                throw SourceImportException("TRUNCATED_REGION", "Cannot establish complete station coverage")
            }

            val mid = (bounds[0] + bounds[2]) / 2
            val middle = (bounds[1] + bounds[3]) / 2

            for (box in listOf(
                listOf(bounds[0], bounds[1], mid, middle), listOf(mid, bounds[1], bounds[2], middle),
                listOf(bounds[0], middle, mid, bounds[3]), listOf(mid, middle, bounds[2], bounds[3])
            )) {
                collect(box, depth + 1, stations)
            }
        } else {
            for (station in response) {
                val id = station.path("id").asString("")
                require(id.isNotBlank() && station.path("lng").isNumber && station.path("lat").isNumber)
                val previous = stations[id]

                if (previous != null && (previous.path("lat") != station.path("lat") || previous.path("lng") != station.path("lng"))) {
                    throw SourceImportException("SOURCE_DATA_CONFLICT", "Station coordinates conflict across tiles")
                }

                stations[id] = station
            }
        }
    }

    private fun request(uri: URI): JsonNode {
        val headers = properties.wherefuel.apiKey.takeIf { it.isNotBlank() }?.let { mapOf("X-Api-Key" to it) }.orEmpty()

        return http.read(uri, headers, beforeAttempt = ::beforeRequest) { json.readTree(it.toFile()) }
    }

    private fun beforeRequest() {
        if (++requests > properties.wherefuel.maxRequests) {
            throw SourceImportException("REQUEST_BUDGET", "WhereFuel request budget exhausted")
        }

        val wait = properties.wherefuel.requestInterval.toNanos() - (System.nanoTime() - lastRequest)

        if (lastRequest != 0L && wait > 0) {
            Thread.sleep(Duration.ofNanos(wait))
        }

        lastRequest = System.nanoTime()
    }

    companion object {
        private val FUELS = mapOf("АИ-92" to "AI_92", "АИ-95" to "AI_95", "ДТ" to "DIESEL")
    }
}
