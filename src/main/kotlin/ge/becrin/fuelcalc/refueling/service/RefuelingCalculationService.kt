package ge.becrin.fuelcalc.refueling.service

import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.prices.model.PriceReadContext
import ge.becrin.fuelcalc.prices.model.SourcePriceRows
import ge.becrin.fuelcalc.prices.model.ComparisonSnapshot
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.forecast.PriceForecastService
import ge.becrin.fuelcalc.prices.service.ComparisonAnalyzer
import ge.becrin.fuelcalc.prices.service.ComparisonConverter
import ge.becrin.fuelcalc.prices.service.ComparisonResolver
import ge.becrin.fuelcalc.prices.service.PriceCellConverter
import ge.becrin.fuelcalc.prices.service.PriceContextConverter
import ge.becrin.fuelcalc.prices.service.PriceSelectionValidator
import ge.becrin.fuelcalc.refueling.RefuelingRelation
import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculateRequestDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculationDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingRowDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingSourceCalculationDto
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.service.SourceUpdateReader
import java.math.BigDecimal
import org.springframework.stereotype.Service

@Service
class RefuelingCalculationService(
    private val archive: ArchiveReadService,

    private val validator: PriceSelectionValidator,

    private val resolver: ComparisonResolver,

    private val comparisons: ComparisonConverter,

    private val analyzer: ComparisonAnalyzer,

    private val cells: PriceCellConverter,

    private val calculator: RefuelingCalculator,

    private val query: PriceQueryExecutor,

    private val converter: PriceContextConverter,

    private val updates: SourceUpdateReader,

    private val forecast: PriceForecastService
) {
    fun calculate(input: RefuelingCalculateRequestDto): RefuelingCalculationDto =
        archive.read { meta ->
            val volume = volume(input.volumeLiters)

            val selection = input.selection
            val selected = validator.resolveAsOf(
                baseCode = selection.baseRegionCode,
                codes = selection.regionCodes,
                fuel = selection.fuelType,
                date = selection.date,
                serverDate = meta.serverDate
            )
            val bounds = query.pageBounds(selected)
            val predicted = forecast.forecast(selected, meta.serverDate)
            val results = PriceSource.entries.map { source ->
                val context = PriceReadContext(selected, source, bounds[source].orEmpty())
                val snapshot =
                    if (selection.date > meta.serverDate) {
                        resolver.resolveLoaded(selected, SourcePriceRows(predicted[source].orEmpty()))
                    } else {
                        val first = context.bounds.values.minOfOrNull { it.first } ?: selected.until

                        resolver.resolve(selected.copy(from = minOf(first, selected.until)), source)
                    }

                calculateSource(context, volume, snapshot, selection.date > meta.serverDate)
            }

            RefuelingCalculationDto(meta, converter.page(selected), volume.setScale(2).toPlainString(), "RUB", results, updates.read())
        }

    private fun calculateSource(
        context: PriceReadContext,
        volume: BigDecimal,
        snapshot: ComparisonSnapshot,
        predicted: Boolean
    ): RefuelingSourceCalculationDto {
        val frequency = if (predicted) PriceFrequency.WEEKLY else context.source.frequency

        if (snapshot.status != ComparisonStatus.AVAILABLE && snapshot.status != ComparisonStatus.SINGLE_REGION) {
            return RefuelingSourceCalculationDto(context.source, frequency, snapshot.status, null, null)
        }

        val totals = calculator.totals(volume, snapshot.rows.associate { it.region.okatoCode to it.price })
        val base = totals.getValue(context.selection.base.okatoCode)
        val comparison = comparisons.convert(analyzer.analyze(snapshot)) { row ->
            val total = totals.getValue(row.region.okatoCode)
            val difference = total.subtract(base)
            val relation =
                when {
                    row.isBase -> RefuelingRelation.BASE
                    difference.signum() < 0 -> RefuelingRelation.LOWER
                    difference.signum() > 0 -> RefuelingRelation.HIGHER
                    else -> RefuelingRelation.EQUAL
                }

            RefuelingRowDto(
                region = RegionRefDto(row.region),
                price = cells.available(checkNotNull(row.price)),
                totalRub = total.toPlainString(),
                differenceRub = difference.toPlainString(),
                relation = relation
            )
        }

        return RefuelingSourceCalculationDto(
            source = context.source,
            frequency = frequency,
            status = snapshot.status,
            baseTotalRub = base.toPlainString(),
            comparison = comparison
        )
    }

    private fun volume(value: String): BigDecimal {
        if (value.length > 10 || !VOLUME.matches(value)) {
            throw ApiException.invalid("volumeLiters", "INVALID_FORMAT", "Требуется строка объёма с максимум двумя десятичными знаками")
        }

        val volume = value.toBigDecimal()

        if (volume < BigDecimal("0.01") || volume > BigDecimal("1000000.00")) {
            throw ApiException.invalid("volumeLiters", "OUT_OF_RANGE", "Допустим объём от 0.01 до 1000000.00 л")
        }

        return volume
    }

    companion object {
        private val VOLUME = Regex("^(0|[1-9][0-9]{0,6})(\\.[0-9]{1,2})?$")
    }
}
