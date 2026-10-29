package ge.becrin.fuelcalc.sources.service

import org.springframework.stereotype.Component

@Component
class RegionCatalog {
    data class Region(
        val code: String,

        val name: String
    )

    private data class Snapshot(
        val regions: List<Region>,

        val byCode: Map<String, Region>,

        val byName: Map<String, Region>
    )

    @Volatile
    private var snapshot = Snapshot(emptyList(), emptyMap(), emptyMap())

    val regions: List<Region>
        get() = snapshot.regions

    val isReady: Boolean
        get() = snapshot.regions.isNotEmpty()

    fun replace(regions: List<Region>) {
        require(regions.isNotEmpty()) { "Region catalog must not be empty" }
        require(regions.all { it.code.matches(Regex("[A-Z0-9][A-Z0-9_-]{0,31}")) && it.name.isNotBlank() }) { "Invalid region entry" }

        val sorted = regions.sortedBy { it.code }
        val byCode = sorted.associateBy { it.code }
        val byName = sorted.associateBy { normalize(it.name) }

        require(byCode.size == sorted.size) { "Duplicate region code" }
        require(byName.size == sorted.size) { "Ambiguous normalized region name" }

        snapshot = Snapshot(sorted, byCode, byName)
    }

    fun byName(name: String): Region? =
        snapshot.byName[normalize(name)]

    fun byCode(code: String): Region? =
        snapshot.byCode[if (code.all(Char::isDigit)) code.padStart(11, '0') else code]

    private fun normalize(name: String): String {
        var value = name.lowercase().replace('ё', 'е').replace('–', '-').replace('—', '-')
            .replace(Regex("^в том числе:\\s*"), "")
            .replace(Regex("\\(([^()]*)\\)")) { match ->
                if (match.value.contains("кроме")) match.value else ""
            }

        if (value.contains("федерального значения")) {
            value = value.replace(Regex("столица российской федерации|город федерального значения|город"), "")
        }

        value = value.trim().replace(Regex("^(?:г\\.|город)\\s+"), "")

        if (!value.contains("кроме")) {
            value = value.replace(Regex("(область|республика)\\s*-.*$"), "$1")
        }

        return value.replace(Regex("(?<![а-я])авт(?:\\.|ономн[а-я]+)"), "авт")
            .replace(Regex("\\s*-\\s*"), "-").replace(Regex("\\s+"), " ").trim()
    }
}
