package ge.becrin.fuelcalc.refueling.service

import ge.becrin.fuelcalc.api.dto.ErrorDetailDto
import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.ComparisonStatus
import ge.becrin.fuelcalc.prices.dto.AvailablePriceCellDto
import ge.becrin.fuelcalc.prices.service.ComparisonConverter
import ge.becrin.fuelcalc.prices.service.ComparisonResolver
import ge.becrin.fuelcalc.prices.service.PriceReadContextFactory
import ge.becrin.fuelcalc.refueling.RefuelingRelation
import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculateRequestDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingCalculationDto
import ge.becrin.fuelcalc.refueling.dto.RefuelingRowDto
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RefuelingCalculationService(
    private val archive: ArchiveReadService,

    private val contexts: PriceReadContextFactory,

    private val resolver: ComparisonResolver,

    private val comparisons: ComparisonConverter,

    private val calculator: RefuelingCalculator
) {
    fun calculate(input: RefuelingCalculateRequestDto): RefuelingCalculationDto =
        archive.read { meta ->
            val volume = volume(input.volumeLiters)

            if (input.expectedSnapshotId != null && input.expectedSnapshotId.length !in 1..128) {
                throw ApiException.invalid("expectedSnapshotId", "INVALID_FORMAT", "Неверная длина ID среза")
            }

            val context = contexts.selection(input.selection, meta.serverDate)
            val snapshot = resolver.resolve(context.selection)

            if (snapshot.status != ComparisonStatus.AVAILABLE && snapshot.status != ComparisonStatus.SINGLE_REGION) {
                val code = if (context.selection.regions.size == 1) "NO_PRICE_IN_RANGE" else "NO_COMMON_PERIOD"

                throw ApiException(
                    422,
                    "CalculationUnavailable",
                    "Нет сопоставимых цен для расчёта",
                    listOf(
                        ErrorDetailDto(null, code, "Выберите другой период или состав регионов")
                    )
                )
            }

            val comparison = comparisons.toDto(snapshot, context)
            val totals = calculator.totals(volume, snapshot.rows.associate { it.region.code to it.price })
            val base = totals.getValue(context.selection.base.code)
            val data = comparison.data.map { row ->
                val total = totals.getValue(row.region.code)
                val difference = total.subtract(base)
                val relation =
                    when {
                        row.isBase -> RefuelingRelation.BASE
                        difference.signum() < 0 -> RefuelingRelation.LOWER
                        difference.signum() > 0 -> RefuelingRelation.HIGHER
                        else -> RefuelingRelation.EQUAL
                    }

                RefuelingRowDto(
                    row.region,
                    row.isBase,
                    checkNotNull(row.price as? AvailablePriceCellDto),
                    total.toPlainString(),
                    difference.toPlainString(),
                    relation
                )
            }

            RefuelingCalculationDto(
                meta,
                context.dto,
                volume.setScale(2).toPlainString(),
                "RUB",
                base.toPlainString(),
                input.expectedSnapshotId,
                input.expectedSnapshotId?.let { it != snapshot.snapshotId },
                comparison,
                data
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
