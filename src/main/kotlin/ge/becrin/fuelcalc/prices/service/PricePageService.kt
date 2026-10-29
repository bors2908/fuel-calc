package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.ComparisonSortBy
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.dto.PricePageDto
import ge.becrin.fuelcalc.prices.dto.PricePageQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceSourcePageDto
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.prices.model.PriceReadContext
import ge.becrin.fuelcalc.prices.model.SourcePriceRows
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.service.SourceUpdateReader
import org.springframework.stereotype.Service

@Service
class PricePageService(
    private val archive: ArchiveReadService,

    private val validator: PriceSelectionValidator,

    private val converter: PriceContextConverter,

    private val query: PriceQueryExecutor,

    private val resolver: ComparisonResolver,

    private val comparisons: ComparisonConverter,

    private val analyzer: ComparisonAnalyzer,

    private val overview: FuelPriceOverviewService,

    private val trend: FuelPriceTrendService,

    private val states: SourceUpdateReader
) {
    fun get(input: PricePageQueryDto): PricePageDto =
        archive.read { meta ->
            val selection = validator.resolve(input, meta.serverDate)
            val rows = query.pageRows(selection).groupBy { it.source }
            val bounds = query.pageBounds(selection)
            val sources = PriceSource.entries.associateWith { source ->
                val prices = SourcePriceRows(rows[source].orEmpty())
                val context = PriceReadContext(selection, source, bounds[source].orEmpty())
                val snapshot = resolver.resolveLoaded(selection, prices)
                val analysis = analyzer.analyze(snapshot, input.sortBy, input.sortOrder)

                PriceSourcePageDto(
                    source.frequency,
                    overview.build(context, prices, analysis),
                    trend.build(selection, prices),
                    comparisons.toDto(analysis, context)
                )
            }

            val ordering =
                if (input.sortBy == ComparisonSortBy.PRICE && sources.getValue(input.sortSource).comparison.status !in comparable) {
                    PriceSource.entries.firstOrNull { sources.getValue(it).comparison.status in comparable } ?: input.sortSource
                } else {
                    input.sortSource
                }

            PricePageDto(meta, converter.page(selection), sources, ordering, states.read())
        }

    companion object {
        private val comparable = setOf(ComparisonStatus.AVAILABLE, ComparisonStatus.SINGLE_REGION)
    }
}
