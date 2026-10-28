package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.fuels.jpa.FuelTypeRepository
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.jpa.PricePeriodRepository
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceSampleEntity
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceSampleRepository
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.sources.jpa.ImportRunRepository
import ge.becrin.fuelcalc.sources.jpa.ImportStore
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalDailySample
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.ImportHash
import ge.becrin.fuelcalc.sources.wherefuel.RegionalStationSampler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class RegionalPriceSampleService(
    private val store: ImportStore,

    private val regions: RegionRepository,

    private val fuels: FuelTypeRepository,

    private val runs: ImportRunRepository,

    private val periods: PricePeriodRepository,

    private val samples: RegionalPriceSampleRepository
) {
    @Transactional
    fun append(
        runId: Long,
        incoming: List<RegionalDailySample>
    ) {
        if (incoming.isEmpty()) {
            return
        }

        store.lockArchive()

        val regionByCode = regions.findSelected(incoming.map { it.regionCode }.distinct()).associateBy { it.code }
        require(incoming.all { it.regionCode in regionByCode }) { "Unmapped region code" }

        val existing = samples.findLatestForDates(
            regions = regionByCode.values,
            dates = incoming.map { it.collectedAt.atZone(ZONE).toLocalDate() }.distinct(),
            policy = RegionalStationSampler.POLICY
        ).associate { Triple(it.region.code, it.fuelType.code, it.sampleDate) to it.contentHash }
        val run = checkNotNull(runs.findWithFeed(runId))
        val rows = incoming.mapNotNull { sample ->
            val date = sample.collectedAt.atZone(ZONE).toLocalDate()

            if (existing[Triple(sample.regionCode, sample.fuelCode, date)] == sample.contentHash) {
                null
            } else {
                RegionalPriceSampleEntity(
                    provider = run.feed.provider,
                    region = regionByCode.getValue(sample.regionCode),
                    fuelType = fuels.getReferenceById(sample.fuelCode),
                    sampleDate = date,
                    samplingPolicyCode = RegionalStationSampler.POLICY,
                    priceRubPerLiter = sample.price,
                    stationCount = sample.stationCount,
                    collectedAt = sample.collectedAt,
                    oldestPriceAt = sample.oldestPriceAt,
                    newestPriceAt = sample.newestPriceAt,
                    firstImportRun = run,
                    contentHash = sample.contentHash
                )
            }
        }

        samples.saveAll(rows)
    }

    @Transactional(readOnly = true)
    fun prepare(
        frequency: PriceFrequency,
        today: LocalDate,
        url: String
    ): List<PreparedPublication> {
        val first = samples.firstDate(RegionalStationSampler.POLICY) ?: return emptyList()
        val completed = periods.findCompleted(frequency, today, first).map { period ->
            PricePeriod(period.frequency, period.referenceDate, period.startDate, period.endDate)
        }.toMutableList()

        if (frequency == PriceFrequency.MONTHLY) {
            var month = first.withDayOfMonth(1)

            while (month.plusMonths(1) <= today) {
                val end = month.plusMonths(1).minusDays(1)

                if (completed.none { it.referenceDate == end }) {
                    completed.add(PricePeriod(frequency, end, month, end))
                }

                month = month.plusMonths(1)
            }
        }

        if (completed.isEmpty()) {
            return emptyList()
        }

        val daily = samples.findLatestInRange(
            from = completed.minOf { checkNotNull(it.startDate) },
            until = completed.maxOf { checkNotNull(it.endDate) },
            policy = RegionalStationSampler.POLICY
        )

        return completed.sortedBy { it.referenceDate }.mapNotNull { period ->
            val start = checkNotNull(period.startDate)
            val end = checkNotNull(period.endDate)
            val expected = ChronoUnit.DAYS.between(start, end).toInt() + 1
            val facts = daily.filter { it.sampleDate in start..end }
                .groupBy { it.region.code to it.fuelType.code }.map { (cell, days) ->
                    val mean = days.fold(BigDecimal.ZERO) { sum, day -> sum + day.priceRubPerLiter }
                        .divide(days.size.toBigDecimal(), 6, RoundingMode.HALF_UP)

                    RegionalPriceFact(
                        cell.first, cell.second, mean, null,
                        days.minOf { it.stationCount }, days.size, expected,
                        days.maxOf { it.collectedAt },
                        ImportHash.hash(days.sortedBy { it.sampleDate }.flatMap {
                            // Keep the persisted hash format used before the JPA migration.
                            listOf(it.sampleDate, it.contentHash, Timestamp.from(it.collectedAt), it.id)
                        })
                    )
                }

            if (facts.isEmpty()) {
                null
            } else {
                PreparedPublication(
                    period, "${RegionalStationSampler.POLICY}:${period.key}", url,
                    "ГдеТопливо · средняя по выборке АЗС, ₽/л · ${RegionalStationSampler.POLICY}", facts
                )
            }
        }
    }

    companion object {
        private val ZONE = ZoneId.of("Europe/Moscow")
    }
}
