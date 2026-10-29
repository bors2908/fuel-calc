package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.prices.model.ComparisonSnapshot
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.model.SourcePriceRows
import ge.becrin.fuelcalc.sources.PriceSource
import org.springframework.stereotype.Service

@Service
class ComparisonResolver(private val query: PriceQueryExecutor) {
    fun resolveLoaded(
        selection: PriceSelection,
        rows: SourcePriceRows
    ): ComparisonSnapshot =
        resolve(selection, rows.latest) {
            rows.byDate.filterValues { day -> day.map { it.region.okatoCode }.distinct().size == selection.regions.size }
                .maxByOrNull { it.key }?.value
        }

    fun resolve(
        selection: PriceSelection,
        source: PriceSource
    ): ComparisonSnapshot =
        resolve(selection, SourcePriceRows(query.boundaries(selection, source)).latest) {
            query.commonPeriod(selection, source)?.let { date -> query.atPeriod(selection, source, date) }
        }

    private fun resolve(
        selection: PriceSelection,
        latest: List<PriceRow>,
        commonRows: () -> List<PriceRow>?
    ): ComparisonSnapshot {
        if (latest.isEmpty()) {
            return ComparisonSnapshot(selection, ComparisonStatus.NO_DATA, emptyList(), null, false)
        }

        if (selection.regions.size == 1) {
            return ComparisonSnapshot(selection, ComparisonStatus.SINGLE_REGION, latest, latest.single().period, false)
        }

        val rows = if (latest.size == selection.regions.size) commonRows() else null

        if (rows == null) {
            return ComparisonSnapshot(selection, ComparisonStatus.NO_COMMON_PERIOD, latest, null, false)
        }

        check(rows.size == selection.regions.size) { "Incomplete common period in read snapshot" }

        val period = rows.first().period

        return ComparisonSnapshot(
            selection = selection,
            status = ComparisonStatus.AVAILABLE,
            rows = rows,
            period = period,
            hasNewerObservations = latest.any { it.period.referenceDate > period.referenceDate }
        )
    }
}
