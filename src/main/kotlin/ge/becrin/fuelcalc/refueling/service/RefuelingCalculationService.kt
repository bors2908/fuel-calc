package ge.becrin.fuelcalc.refueling.service

import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.dto.PriceSelectionDto
import ge.becrin.fuelcalc.prices.service.ComparisonConverter
import ge.becrin.fuelcalc.prices.service.ComparisonResolver
import ge.becrin.fuelcalc.prices.service.PriceCellConverter
import ge.becrin.fuelcalc.prices.service.PriceReadContextFactory
import ge.becrin.fuelcalc.refueling.RefuelingRelation
import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculateRequestDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculationDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingRowDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingSourceCalculationDto
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.service.SourceUpdateReader
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class RefuelingCalculationService(
    private val archive: ArchiveReadService,

    private val contexts: PriceReadContextFactory,

    private val resolver: ComparisonResolver,

    private val comparisons: ComparisonConverter,

    private val cells: PriceCellConverter,

    private val calculator: RefuelingCalculator,

    private val updates: SourceUpdateReader
) {
    fun calculate(input: RefuelingCalculateRequestDto): RefuelingCalculationDto =
        archive.read { meta ->
            val volume = volume(input.volumeLiters)

            if (input.selection.date > meta.serverDate) {
                throw ApiException.invalid("selection.date", "FUTURE_DATE", "Дата не должна быть в будущем")
            }

            for ((source, id) in input.expectedSnapshotIds) {
                if (id.length !in 1..128) {
                    throw ApiException.invalid("expectedSnapshotIds.$source", "INVALID_FORMAT", "Неверная длина ID среза")
                }
            }

            val results = PriceSource.entries.map { source -> calculateSource(input, volume, source, meta.serverDate) }

            RefuelingCalculationDto(meta, volume.setScale(2).toPlainString(), "RUB", results, updates.read())
        }

    private fun calculateSource(
        input: RefuelingCalculateRequestDto,
        volume: BigDecimal,
        source: PriceSource,
        serverDate: LocalDate
    ): RefuelingSourceCalculationDto {
        val context = contexts.selection(selection(input, source), serverDate)
        val first = context.bounds.values.minOfOrNull { it.first } ?: input.selection.date
        val snapshot = resolver.resolve(context.selection.copy(from = minOf(first, input.selection.date)))
        val changed = input.expectedSnapshotIds[source]?.let { it != snapshot.snapshotId }

        if (snapshot.status != ComparisonStatus.AVAILABLE && snapshot.status != ComparisonStatus.SINGLE_REGION) {
            return RefuelingSourceCalculationDto(context.dto, snapshot.status, null, changed, null)
        }

        val totals = calculator.totals(volume, snapshot.rows.associate { it.region.okatoCode to it.price })
        val base = totals.getValue(context.selection.base.okatoCode)
        val comparison = comparisons.convert(snapshot) { row ->
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
                region = row.region,
                price = cells.available(checkNotNull(row.price)),
                latestAvailableDate = context.bounds[row.region.okatoCode]?.last,
                differenceRubPerLiter = row.difference?.toPlainString(),
                isLowest = row.isLowest,
                isHighest = row.isHighest,
                totalRub = total.toPlainString(),
                differenceRub = difference.toPlainString(),
                relation = relation
            )
        }

        return RefuelingSourceCalculationDto(context.dto, snapshot.status, base.toPlainString(), changed, comparison)
    }

    private fun selection(
        input: RefuelingCalculateRequestDto,
        source: PriceSource
    ): PriceSelectionDto =
        PriceSelectionDto(
            input.selection.baseRegionCode,
            input.selection.regionCodes,
            input.selection.fuelType,
            input.selection.date,
            input.selection.date,
            source.frequency,
            source
        )

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
