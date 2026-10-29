package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.prices.model.ComparisonSnapshot
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import org.springframework.stereotype.Service

@Service
class ComparisonResolver(
    private val query: PriceQueryExecutor,

    private val identity: PriceIdentityFactory
) {
    fun resolve(
        selection: PriceSelection,
        boundaries: List<PriceRow> = query.boundaries(selection)
    ): ComparisonSnapshot {
        val latest = boundaries.groupBy { it.region.okatoCode }.values.map { rows -> rows.maxBy { it.period.referenceDate } }

        if (latest.isEmpty()) {
            return ComparisonSnapshot(selection, ComparisonStatus.NO_DATA, emptyList(), null, null, false)
        }

        if (selection.regions.size == 1) {
            return ComparisonSnapshot(
                selection,
                ComparisonStatus.SINGLE_REGION,
                latest,
                latest.single().period,
                identity.snapshot(selection, latest),
                false
            )
        }

        val periodId = if (latest.size == selection.regions.size) query.commonPeriod(selection) else null

        if (periodId == null) {
            return ComparisonSnapshot(selection, ComparisonStatus.NO_COMMON_PERIOD, latest, null, null, false)
        }

        val rows = query.atPeriod(selection, periodId)
        check(rows.size == selection.regions.size) { "Incomplete common period in read snapshot" }
        val period = rows.first().period

        return ComparisonSnapshot(
            selection,
            ComparisonStatus.AVAILABLE,
            rows,
            period,
            identity.snapshot(selection, rows),
            latest.any { it.period.referenceDate > period.referenceDate }
        )
    }
}
