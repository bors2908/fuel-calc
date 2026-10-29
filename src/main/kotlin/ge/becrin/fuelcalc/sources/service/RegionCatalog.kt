package ge.becrin.fuelcalc.sources.service

import ge.becrin.fuelcalc.regions.model.Region
import org.springframework.stereotype.Component

@Component
class RegionCatalog {
    @Volatile
    private var snapshot: Map<String, Region> = emptyMap()

    val regions: List<Region>
        get() = snapshot.values.toList()

    val isReady: Boolean
        get() = snapshot.isNotEmpty()

    fun replace(regions: List<Region>) {
        require(regions.isNotEmpty()) { "Region catalog must not be empty" }
        require(regions.all { it.okatoCode.isNotBlank() && it.name.isNotBlank() }) { "Invalid region entry" }

        val byCode = regions.sortedBy { it.okatoCode }.associateBy { it.okatoCode }

        require(byCode.size == regions.size) { "Duplicate OKATO code" }

        snapshot = byCode
    }

    fun byOkatoCode(code: String): Region? =
        snapshot[code]
}
