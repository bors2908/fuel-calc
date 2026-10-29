package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.fuels.jpa.FuelTypeRepository
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.PricePeriodRepository
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.sources.jpa.ImportRunRepository
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.ImportHash
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Service
class BenzupArchiveService(
    private val store: ImportStore,

    private val regions: RegionRepository,

    private val fuels: FuelTypeRepository,

    private val runs: ImportRunRepository,

    private val periods: PricePeriodRepository,

    private val measurements: BenzupMeasurementRepository,

    private val daily: BenzupDailyPriceRepository
) {
    @Transactional
    fun record(
        runId: Long,
        prices: Map<Pair<String, String>, BigDecimal>,
        collectedAt: Instant
    ) {
        require(prices.isNotEmpty())

        store.lockArchive()

        // A retry of the same persisted run must not increase the weight of its snapshot.
        if (measurements.findForRun(runId).isNotEmpty()) {
            return
        }

        val date = collectedAt.atZone(ZONE).toLocalDate()
        val run = checkNotNull(runs.findWithFeed(runId))
        require(run.feed.provider.code == "BENZUP")

        val regionByCode = regions.findSelected(prices.keys.map { it.first }.distinct()).associateBy { it.code }
        require(prices.keys.all { it.first in regionByCode })

        val existing = daily.findForDate(date).associateBy { it.region.code to it.fuelType.code }
        val incoming = prices.map { (cell, price) ->
            require(price > BigDecimal.ZERO)

            BenzupMeasurementEntity(
                region = regionByCode.getValue(cell.first),
                fuelType = fuels.getReferenceById(cell.second),
                importRun = run,
                observedDate = date,
                priceRubPerLiter = price,
                collectedAt = collectedAt
            )
        }
        val aggregates = incoming.map { measurement ->
            val cell = measurement.region.code to measurement.fuelType.code
            val aggregate = existing[cell]
                ?: BenzupDailyPriceEntity(
                    region = measurement.region,
                    fuelType = measurement.fuelType,
                    observedDate = date,
                    priceSum = BigDecimal.ZERO,
                    measurementCount = 0,
                    priceRubPerLiter = BigDecimal.ZERO,
                    collectedAt = collectedAt
                )
            aggregate.priceSum += measurement.priceRubPerLiter
            aggregate.measurementCount++
            aggregate.priceRubPerLiter = aggregate.priceSum.divide(aggregate.measurementCount.toBigDecimal(), 6, RoundingMode.HALF_UP)
            aggregate.collectedAt = maxOf(aggregate.collectedAt, collectedAt)

            aggregate
        }

        measurements.saveAll(incoming)
        daily.saveAll(aggregates)
    }

    @Transactional(readOnly = true)
    fun prepare(
        frequency: PriceFrequency,
        today: LocalDate,
        since: LocalDate?,
        url: String
    ): List<PreparedPublication> {
        val first = daily.firstDate() ?: return emptyList()
        val from = maxOf(first, since?.minusDays(1) ?: first)
        val calendar = mutableMapOf<LocalDate, PricePeriod>()

        if (frequency != PriceFrequency.DAILY) {
            for (period in periods.findCompleted(frequency, today, from)) {
                calendar[period.referenceDate] = PricePeriod(frequency, period.referenceDate, period.startDate, period.endDate)
            }
        }

        var start =
            when (frequency) {
                PriceFrequency.DAILY -> from
                PriceFrequency.WEEKLY -> from.with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY))
                PriceFrequency.MONTHLY -> from.withDayOfMonth(1)
            }

        while (start <= today) {
            val end =
                when (frequency) {
                    PriceFrequency.DAILY -> start
                    PriceFrequency.WEEKLY -> start.plusDays(6)
                    PriceFrequency.MONTHLY -> start.plusMonths(1).minusDays(1)
                }

            if (frequency == PriceFrequency.DAILY || end < today) {
                calendar.putIfAbsent(end, PricePeriod(frequency, end, start, end))
            }

            start = end.plusDays(1)
        }

        if (calendar.isEmpty()) {
            return emptyList()
        }

        val history = daily.findHistory(calendar.values.minOf { checkNotNull(it.startDate) }, calendar.keys.max())

        return calendar.values.sortedBy { it.referenceDate }.mapNotNull { period ->
            val lower = checkNotNull(period.startDate)
            val upper = checkNotNull(period.endDate)
            val expected = ChronoUnit.DAYS.between(lower, upper).toInt() + 1
            val facts = history.filter { it.observedDate in lower..upper }
                .groupBy { it.region.code to it.fuelType.code }.map { (cell, days) ->
                    RegionalPriceFact(
                        regionCode = cell.first,
                        fuelCode = cell.second,
                        price = days.fold(BigDecimal.ZERO) { sum, day -> sum + day.priceRubPerLiter }
                            .divide(days.size.toBigDecimal(), 6, RoundingMode.HALF_UP),
                        sampleMeasurementCount = days.sumOf { it.measurementCount },
                        sampleDayCount = days.size,
                        sampleExpectedDayCount = expected,
                        sampleCollectedAt = days.maxOf { it.collectedAt },
                        sampleInputHash = ImportHash.hash(days.flatMap {
                            listOf(it.observedDate, it.priceSum, it.measurementCount, it.collectedAt)
                        })
                    )
                }

            if (facts.isEmpty()) {
                null
            } else {
                PreparedPublication(
                    period = period,
                    externalKey = "$POLICY:${period.key}",
                    url = url,
                    attribution = "BenzUp · региональный индекс; среднее замеров за день, " +
                        "затем среднее доступных дней, ₽/л",
                    facts = facts
                )
            }
        }
    }

    companion object {
        const val POLICY = "BENZUP_DAILY_MEAN_V1"

        val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    }
}
