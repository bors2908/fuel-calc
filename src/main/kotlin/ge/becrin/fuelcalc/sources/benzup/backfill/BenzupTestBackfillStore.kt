package ge.becrin.fuelcalc.sources.benzup.backfill

import ge.becrin.fuelcalc.archive.jpa.ArchiveStateRepository
import ge.becrin.fuelcalc.prices.jpa.FuelPriceEntity
import ge.becrin.fuelcalc.prices.jpa.FuelPriceRepository
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
import ge.becrin.fuelcalc.sources.config.ImportProperties
import ge.becrin.fuelcalc.sources.jpa.ImportStateRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.NavigableMap
import java.util.TreeMap
import kotlin.random.Random
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@ConditionalOnProperty(prefix = "imports.benzup", name = ["test-backfill-enabled"], havingValue = "true")
class BenzupTestBackfillStore(
    private val archive: ArchiveStateRepository,

    private val prices: FuelPriceRepository,

    private val states: ImportStateRepository,

    private val properties: ImportProperties,

    private val clock: Clock
) {
    data class Result(
        val inserted: Int = 0,

        val skippedSeries: Int = 0,

        val missingOfficialDays: Int = 0
    )

    @Transactional
    fun fill(): Result {
        archive.lockForPublication()

        if (states.findById(PriceSource.MARKET.name).orElse(null)?.lastSuccessAt == null) {
            return Result()
        }

        val start = properties.benzup.startDate
        val anchors = prices.findFirstObservations(PriceSource.MARKET).filter { it.referenceDate > start }
        val until = anchors.maxOfOrNull { it.referenceDate } ?: return Result()
        val official = prices.findWindow(PriceSource.OFFICIAL, start.minusWeeks(1), until)
            .groupBy { it.region.okatoCode to it.fuel }
            .mapValues { (_, rows) -> TreeMap(rows.associate { it.referenceDate to it.priceRubPerLiter }) }
        val now = clock.instant()
        val generated = mutableListOf<FuelPriceEntity>()
        var skippedSeries = 0
        var missingDays = 0

        for (anchor in anchors) {
            val weeks = official[anchor.region.okatoCode to anchor.fuel]
            val anchorOfficial = interpolatedWeeklyPrice(weeks, anchor.referenceDate)

            if (anchorOfficial == null) {
                skippedSeries++

                continue
            }

            val delta = anchor.priceRubPerLiter.subtract(anchorOfficial)
            var date = start

            while (date < anchor.referenceDate) {
                val officialPrice = interpolatedWeeklyPrice(weeks, date)

                if (officialPrice == null) {
                    missingDays++
                } else {
                    val jitter = BigDecimal.valueOf(Random.nextLong(-10, 11), 2)
                    val price = officialPrice.add(delta).add(jitter).setScale(2, RoundingMode.HALF_UP)

                    require(price.signum() > 0) {
                        "Non-positive test price [region=${anchor.region.okatoCode}, fuel=${anchor.fuel}, date=$date]"
                    }

                    generated.add(FuelPriceEntity(
                        region = anchor.region,
                        fuel = anchor.fuel,
                        source = PriceSource.MARKET,
                        frequency = PriceSource.MARKET.frequency,
                        provider = SourceProvider.BENZUP,
                        referenceDate = date,
                        priceRubPerLiter = price,
                        loadedAt = now
                    ))
                }

                date = date.plusDays(1)
            }
        }

        if (generated.isNotEmpty()) {
            prices.saveAll(generated)
            archive.advanceRevision(now)
        }

        return Result(generated.size, skippedSeries, missingDays)
    }

    private fun interpolatedWeeklyPrice(
        weeks: NavigableMap<LocalDate, BigDecimal>?,
        date: LocalDate
    ): BigDecimal? {
        val current = weeks?.floorEntry(date) ?: return null

        if (date >= current.key.plusWeeks(1)) {
            return null
        }

        val next = weeks.higherEntry(current.key)

        if (next == null || next.key != current.key.plusWeeks(1)) {
            return current.value
        }

        val elapsedDays = ChronoUnit.DAYS.between(current.key, date)
        val shift = next.value.subtract(current.value)
            .multiply(BigDecimal.valueOf(elapsedDays))
            .divide(BigDecimal.valueOf(7), 8, RoundingMode.HALF_UP)

        return current.value.add(shift)
    }
}
