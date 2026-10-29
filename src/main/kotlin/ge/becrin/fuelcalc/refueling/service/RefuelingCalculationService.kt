package ge.becrin.fuelcalc.refueling.service

import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.prices.model.PriceReadContext
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

    private val updates: SourceUpdateReader
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
            val results = PriceSource.entries.map { source ->
                calculateSource(PriceReadContext(selected, source, bounds[source].orEmpty()), volume)
            }

            RefuelingCalculationDto(meta, converter.page(selected), volume.setScale(2).toPlainString(), "RUB", results, updates.read())
        }

    private fun calculateSource(
        context: PriceReadContext,
        volume: BigDecimal
    ): RefuelingSourceCalculationDto {
        val first = context.bounds.values.minOfOrNull { it.first } ?: context.selection.until
        val snapshot = resolver.resolve(context.selection.copy(from = minOf(first, context.selection.until)), context.source)

        if (snapshot.status != ComparisonStatus.AVAILABLE && snapshot.status != ComparisonStatus.SINGLE_REGION) {
            return RefuelingSourceCalculationDto(context.source, context.source.frequency, snapshot.status, null, null)
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
            frequency = context.source.frequency,
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
