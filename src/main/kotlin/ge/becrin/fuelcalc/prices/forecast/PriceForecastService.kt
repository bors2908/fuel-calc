package ge.becrin.fuelcalc.prices.forecast

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.prices.model.PricePeriod
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.sources.PriceSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.TreeMap
import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.prices.PriceSeriesStatus
import ge.becrin.fuelcalc.prices.dto.RegionPriceSeriesDto
import ge.becrin.fuelcalc.prices.dto.TrendPointDto
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import org.springframework.stereotype.Service

@Service
class PriceForecastService(private val query: PriceQueryExecutor) {
    fun forecast(
        selection: PriceSelection,
        today: LocalDate
    ): Map<PriceSource, List<PriceRow>> {
        require(selection.until <= today.plusYears(1)) { "Forecast horizon exceeds one calendar year" }

        if (selection.until <= today) {
            return emptyMap()
        }

        val history = query.pageRows(selection.copy(from = today.minusYears(3).minusDays(6), until = today))
        val byRegion = history.groupBy { it.region.okatoCode }
        val generated = PriceSource.entries.associateWith { mutableListOf<PriceRow>() }

        for (region in selection.regions) {
            val rows = byRegion[region.okatoCode].orEmpty()
            val official = TreeMap(rows.filter { it.source == PriceSource.OFFICIAL }.associateBy { it.period.referenceDate })
            val anchors = rows.groupBy { it.source }.mapValues { (_, prices) -> prices.maxBy { it.period.referenceDate } }
            var date = weekStart(maxOf(selection.from, today.plusDays(1)), today)

            while (date <= selection.until) {
                val changes = (1L..3L).mapNotNull { yearsAgo ->
                    val baseDate = today.minusYears(yearsAgo)
                    val targetDate = date.minusYears(yearsAgo)
                    val base = official.floorEntry(baseDate)?.value
                        ?.takeIf { baseDate < it.period.referenceDate.plusWeeks(1) }
                    val target = official.floorEntry(targetDate)?.value
                        ?.takeIf { targetDate < it.period.referenceDate.plusWeeks(1) }

                    if (base == null || target == null || base.price.signum() <= 0) {
                        null
                    } else {
                        baseDate.year to target.price.divide(base.price, 12, RoundingMode.HALF_UP).subtract(BigDecimal.ONE)
                    }
                }

                if (changes.isNotEmpty()) {
                    val growth = changes.map { it.second }.reduce(BigDecimal::add)
                        .divide(BigDecimal.valueOf(changes.size.toLong()), 12, RoundingMode.HALF_UP)

                    for ((source, anchor) in anchors) {
                        val price = anchor.price.multiply(BigDecimal.ONE.add(growth)).setScale(2, RoundingMode.HALF_UP)

                        if (price.signum() > 0) {
                            generated.getValue(source).add(PriceRow(
                                id = null,
                                region = region,
                                fuel = selection.fuel,
                                period = PricePeriod(PriceFrequency.WEEKLY, date),
                                price = price,
                                source = source,
                                forecast = ForecastBasis(
                                    asOfDate = today,
                                    baseDate = anchor.period.referenceDate,
                                    basePriceRubPerLiter = anchor.price.toPlainString(),
                                    historicalYears = changes.map { it.first },
                                    growthPercent = growth.movePointRight(2).setScale(6, RoundingMode.HALF_UP).toPlainString()
                                )
                            ))
                        }
                    }
                }

                date = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            }
        }

        return generated
    }

    fun trend(
        selection: PriceSelection,
        today: LocalDate,
        rows: List<PriceRow>
    ): List<RegionPriceSeriesDto> =
        selection.regions.map { region ->
            val available = rows.filter { it.region.okatoCode == region.okatoCode }.associateBy { it.period.referenceDate }
            val points = mutableListOf<TrendPointDto>()
            var date = weekStart(maxOf(selection.from, today.plusDays(1)), today)

            while (date <= selection.until) {
                val row = available[date]

                points.add(TrendPointDto(
                    date = date,
                    priceRubPerLiter = row?.price?.toPlainString(),
                    observationId = null,
                    missingReason = if (row == null) MissingPriceReason.INSUFFICIENT_HISTORY else null,
                    forecast = row?.forecast,
                    kind = "FORECAST"
                ))

                date = date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            }

            RegionPriceSeriesDto(
                RegionRefDto(region),
                if (available.isEmpty()) PriceSeriesStatus.NO_DATA else PriceSeriesStatus.AVAILABLE,
                points
            )
        }

    fun weekStart(
        date: LocalDate,
        today: LocalDate
    ): LocalDate =
        maxOf(today.plusDays(1), date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
}
