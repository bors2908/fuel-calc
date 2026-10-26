package ge.becrin.fuelcalc.sources.wherefuel

data class RegionBoundary(
    val regionCode: String,

    val polygons: List<List<List<Pair<Double, Double>>>>
) {
    val bounds: List<Double>
        get() {
            val points = polygons.flatMap { it.first() }

            return listOf(points.minOf { it.first }, points.minOf { it.second }, points.maxOf { it.first }, points.maxOf { it.second })
        }

    fun contains(
        longitude: Double,
        latitude: Double
    ): Boolean =
        polygons.any { rings -> inside(rings.first(), longitude, latitude) && rings.drop(1).none { inside(it, longitude, latitude) } }

    private fun inside(
        ring: List<Pair<Double, Double>>,
        longitude: Double,
        latitude: Double
    ): Boolean {
        var inside = false
        var previous = ring.last()

        for (point in ring) {
            val (x, y) = point
            val (px, py) = previous

            if ((y > latitude) != (py > latitude) && longitude < (px - x) * (latitude - y) / (py - y) + x) {
                inside = !inside
            }

            previous = point
        }

        return inside
    }
}
