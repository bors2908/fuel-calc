package ge.becrin.fuelcalc.sources.rustata

import ge.becrin.fuelcalc.prices.jpa.PricePeriodRepository
import ge.becrin.fuelcalc.prices.jpa.RegionalPriceKeyRepository
import ge.becrin.fuelcalc.sources.model.FeedDefinition
import ge.becrin.fuelcalc.sources.model.FetchWindow
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class RustataWeekResolver(
    private val periods: PricePeriodRepository,

    private val keys: RegionalPriceKeyRepository
) {
    @Transactional(readOnly = true)
    fun resolve(
        feed: FeedDefinition,
        window: FetchWindow,
        factsByDate: Map<LocalDate, List<RegionalPriceFact>>
    ): Map<LocalDate, PricePeriod> {
        if (factsByDate.isEmpty()) {
            return emptyMap()
        }

        val confirmed = periods.findOfficialWeeks(factsByDate.keys.min(), factsByDate.keys.max()).map { period ->
            PricePeriod(period.frequency, period.referenceDate, period.startDate, period.endDate)
        }
        val calendar = confirmed.associateBy { it.referenceDate }
        val resolved = factsByDate.keys.associateWith { date ->
            calendar[date] ?: confirmed.singleOrNull { date in checkNotNull(it.startDate)..checkNotNull(it.endDate) }
        }.toMutableMap()
        val shifted = resolved.filter { (date, period) -> period != null && period.referenceDate != date }

        if (shifted.isNotEmpty()) {
            val official = keys.findOfficialAtDates(shifted.values.map { checkNotNull(it).referenceDate }.distinct())
                .groupBy { it.period.referenceDate }

            for ((date, candidate) in shifted) {
                val period = checkNotNull(candidate)
                val prices = official[period.referenceDate].orEmpty().associate {
                    (it.region.code to it.fuelType.code) to checkNotNull(it.currentObservation).priceRubPerLiter
                }
                val facts = factsByDate.getValue(date)
                val matched = facts.all { fact -> prices[fact.regionCode to fact.fuelCode]?.compareTo(fact.price) == 0 }

                if (matched) {
                    log.warn(
                        "Rustata weekly label normalized against official prices [sourceDate={}, referenceDate={}, " +
                            "startDate={}, endDate={}, verifiedFacts={}]",
                        date, period.referenceDate, period.startDate, period.endDate, facts.size
                    )
                } else {
                    resolved[date] = null
                }
            }
        }

        val duplicates = resolved.values.filterNotNull().groupingBy { it.referenceDate }.eachCount().filterValues { it > 1 }.keys
        val missing = resolved.filter { (_, period) -> period == null || period.referenceDate in duplicates }.keys.sorted()

        if (missing.isNotEmpty()) {
            throw SourceImportException(
                "UNCONFIRMED_WEEK",
                "Rustata weekly dates have no unambiguous verified Rosstat periods " +
                    "[dataset=${feed.externalCode}, from=${window.from}, until=${window.until}, " +
                    "missingCount=${missing.size}, missingDates=${missing.take(12)}, confirmedCount=${calendar.size}]. " +
                    "Check preceding ROSSTAT_WEEKLY_V1 errors, calendar intervals and official price agreement"
            )
        }

        return resolved.mapValues { checkNotNull(it.value) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RustataWeekResolver::class.java)
    }
}
