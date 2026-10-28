package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.service.PriceIdentityFactory
import ge.becrin.fuelcalc.prices.service.SourceFallbackPolicy
import ge.becrin.fuelcalc.refueling.service.RefuelingCalculator
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceAggregationMethod
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.dto.SourceLabelDto
import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PriceDomainTest {
    private val policy = SourceFallbackPolicy()

    private val identity = PriceIdentityFactory(policy)

    private val first = RegionRefDto("A", "Регион А")

    private val second = RegionRefDto("B", "Регион Б")

    private val date = LocalDate.of(2026, 10, 19)

    private val selection = PriceSelection(
        first,
        listOf(first, second),
        FuelTypeRefDto(FuelType.DIESEL, "Дизель"),
        date.minusMonths(1),
        date,
        PriceFrequency.WEEKLY,
        PriceSource.ROSSTAT
    )

    @Test
    fun testFallbackOrdersCoverThreeIndependentChannels() {
        assertEquals(listOf(PriceSource.ROSSTAT, PriceSource.RUSTATA, PriceSource.WHEREFUEL), policy.order(PriceSource.ROSSTAT))
        assertEquals(listOf(PriceSource.RUSTATA, PriceSource.ROSSTAT, PriceSource.WHEREFUEL), policy.order(PriceSource.RUSTATA))
        assertEquals(listOf(PriceSource.WHEREFUEL, PriceSource.RUSTATA, PriceSource.ROSSTAT), policy.order(PriceSource.WHEREFUEL))
    }

    @Test
    fun testIdentitiesDependOnCanonicalSelectionAndImmutableObservationIds() {
        val rows = listOf(row(1, first), row(2, second))
        val reordered = selection.copy(regions = listOf(second, first))

        assertEquals(identity.context(selection).key, identity.context(reordered).key)
        assertEquals(identity.snapshot(selection, rows), identity.snapshot(reordered, rows.reversed()))
        assertNotEquals(identity.context(selection).key, identity.context(selection.copy(base = second)).key)
        assertNotEquals(identity.snapshot(selection, rows), identity.snapshot(selection.copy(preferred = PriceSource.RUSTATA), rows))
        assertNotEquals(identity.snapshot(selection, rows), identity.snapshot(selection, listOf(rows.first().copy(id = 3), rows.last())))
    }

    @Test
    fun testCalculatorUsesExactDecimalsAndHalfUpForFarmVolumes() {
        val calculator = RefuelingCalculator()
        val result = calculator.totals(
            "600".toBigDecimal(),
            mapOf(
                "A" to "72.50".toBigDecimal(),
                "B" to "71.80".toBigDecimal(),
                "C" to "73.10".toBigDecimal()
            )
        )

        assertEquals("43500.00", result.getValue("A").toPlainString())
        assertEquals("43080.00", result.getValue("B").toPlainString())
        assertEquals("43860.00", result.getValue("C").toPlainString())
        assertEquals(
            "0.01",
            calculator.totals("0.5".toBigDecimal(), mapOf("A" to "0.01".toBigDecimal()))
            .getValue("A").toPlainString()
        )
        assertEquals(
            "72123456.79",
            calculator.totals(
                "1000000".toBigDecimal(),
                mapOf("A" to "72.123456789012345678".toBigDecimal())
            ).getValue("A").toPlainString()
        )
    }

    private fun row(
        id: Long,
        region: RegionRefDto
    ): PriceRow {
        val source = SourceLabelDto("ROSSTAT", "Росстат")

        return PriceRow(
            id,
            region,
            selection.fuel,
            PricePeriodDto("WEEKLY:$date", PriceFrequency.WEEKLY, date, date.toString(), date.minusDays(6), date),
            "72.50".toBigDecimal(),
            SourceSummaryDto(source, "ROSSTAT_WEEKLY", PriceAggregationMethod.OFFICIAL_STATISTICS)
        )
    }
}
