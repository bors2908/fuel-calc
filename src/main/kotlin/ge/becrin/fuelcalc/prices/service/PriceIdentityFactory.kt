package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.prices.dto.PriceContextDto
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.HexFormat

@Component
class PriceIdentityFactory(private val policy: SourceFallbackPolicy) {
    fun context(selection: PriceSelection): PriceContextDto =
        PriceContextDto(
            digest(listOf("context-v1") + canonical(selection)),
            selection.base,
            selection.regions,
            selection.fuel,
            selection.from,
            selection.until,
            selection.frequency,
            selection.preferred,
            policy.order(selection.preferred),
            PriceBasis.REGIONAL_CONSUMER_AVERAGE,
            "RUB/L"
        )

    fun snapshot(
        selection: PriceSelection,
        rows: List<PriceRow>
    ): String =
        digest(
            listOf("snapshot-v1") + canonical(selection) + rows.sortedBy { it.region.code }.flatMap {
                listOf(it.region.code, it.period.key, it.id.toString())
            }
        )

    private fun canonical(selection: PriceSelection): List<String> =
        listOf(
            selection.base.code,
            selection.fuel.code.name,
            selection.from.toString(),
            selection.until.toString(),
            selection.frequency.name,
            selection.preferred.name,
            PriceBasis.REGIONAL_CONSUMER_AVERAGE.name,
            "RUB/L"
        ) +
            selection.regions.map { it.code }.sorted()

    private fun digest(parts: List<String>): String {
        val canonical = parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)

        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical))
    }
}
