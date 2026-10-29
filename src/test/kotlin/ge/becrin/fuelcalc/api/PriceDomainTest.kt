package ge.becrin.fuelcalc.api

import ge.becrin.fuelcalc.api.SortOrder
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.ComparisonSortBy
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.model.ComparisonSnapshot
import ge.becrin.fuelcalc.prices.model.PricePeriod
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.service.ComparisonAnalyzer
import ge.becrin.fuelcalc.refueling.service.RefuelingCalculator
import ge.becrin.fuelcalc.regions.model.Region
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PriceDomainTest {
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


    @Test
    fun testComparisonKeepsStableTieOrderAndSharesMinimumAndDifferenceRules() {
        val date = LocalDate.of(2026, 10, 19)
        val regions = listOf(Region("C", "Одинаковое имя"), Region("B", "Одинаковое имя"), Region("A", "Основной"))
        val selection = PriceSelection(regions.last(), regions, FuelType.DIESEL, date, date)
        val period = PricePeriod(PriceFrequency.WEEKLY, date)
        val rows = regions.mapIndexed { index, region ->
            PriceRow(index.toLong() + 1, region, FuelType.DIESEL, period,
                (if (region.okatoCode == "A") "73.10" else "71.80").toBigDecimal(), PriceSource.OFFICIAL)
        }
        val snapshot = ComparisonSnapshot(selection, ComparisonStatus.AVAILABLE, rows, period, false)
        val analysis = ComparisonAnalyzer().analyze(snapshot)

        assertEquals(listOf("B", "C", "A"), analysis.rows.map { it.region.okatoCode })
        assertEquals(listOf("B", "C"), analysis.lowestRegions.map { it.okatoCode })
        assertEquals("71.80", analysis.minimum?.toPlainString())
        assertEquals("-1.30", analysis.rows.first().difference?.toPlainString())
        assertTrue(analysis.rows.take(2).all { it.isLowest })
        assertEquals(false, analysis.allPricesEqual)

        val descending = ComparisonAnalyzer().analyze(snapshot, sortOrder = SortOrder.DESC)

        assertEquals(listOf("A", "B", "C"), descending.rows.map { it.region.okatoCode })
    }

    @Test
    fun testComparisonWithoutCommonPeriodUsesNamesAndDoesNotComparePrices() {
        val date = LocalDate.of(2026, 10, 19)
        val base = Region("A", "Регион А")
        val other = Region("B", "Регион Б")
        val selection = PriceSelection(base, listOf(base, other), FuelType.DIESEL, date, date.plusDays(7))
        val rows = listOf(
            PriceRow(1, base, FuelType.DIESEL, PricePeriod(PriceFrequency.WEEKLY, date), "73.10".toBigDecimal(), PriceSource.OFFICIAL),
            PriceRow(2, other, FuelType.DIESEL, PricePeriod(PriceFrequency.WEEKLY, date.plusDays(7)),
                "71.80".toBigDecimal(), PriceSource.OFFICIAL)
        )
        val snapshot = ComparisonSnapshot(selection, ComparisonStatus.NO_COMMON_PERIOD, rows, null, false)
        val analysis = ComparisonAnalyzer().analyze(snapshot, sortOrder = SortOrder.DESC)

        assertEquals(ComparisonSortBy.REGION_NAME, analysis.sortBy)
        assertEquals(SortOrder.ASC, analysis.sortOrder)
        assertEquals(listOf("A", "B"), analysis.rows.map { it.region.okatoCode })
        assertNull(analysis.minimum)
        assertNull(analysis.allPricesEqual)
        assertTrue(analysis.rows.all { it.difference == null })
        assertFalse(analysis.rows.any { it.isLowest })
    }
}
