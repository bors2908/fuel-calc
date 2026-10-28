package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.fuels.jpa.FuelTypeRepository
import ge.becrin.fuelcalc.prices.dto.FuelPriceQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceSelectionDto
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PriceSelectionValidator(
    private val regions: RegionRepository,

    private val fuels: FuelTypeRepository
) {
    fun resolve(
        query: FuelPriceQueryDto,
        serverDate: LocalDate
    ): PriceSelection =
        resolve(
            PriceSelectionDto(
                checkNotNull(query.baseRegionCode),
                csv(checkNotNull(query.filterRegionCode), "filterRegionCode"),
                checkNotNull(query.filterFuelType),
                checkNotNull(query.filterDateFrom),
                checkNotNull(query.filterDateTo),
                checkNotNull(query.frequency),
                checkNotNull(query.preferredSource)
            ),
            serverDate,
            false
        )

    fun resolve(
        input: PriceSelectionDto,
        serverDate: LocalDate,
        json: Boolean = true
    ): PriceSelection {
        val regionField = if (json) "selection.regionCodes" else "filterRegionCode"
        val baseField = if (json) "selection.baseRegionCode" else "baseRegionCode"
        val fromField = if (json) "selection.dateFrom" else "filterDateFrom"
        val untilField = if (json) "selection.dateTo" else "filterDateTo"

        if (!CODE.matches(input.baseRegionCode)) {
            throw ApiException.invalid(baseField, "INVALID_FORMAT", "Неверный код основного региона")
        }

        if (input.baseRegionCode !in input.regionCodes) {
            throw ApiException.invalid(baseField, "BASE_REGION_NOT_SELECTED", "Основной регион должен входить в выбор")
        }

        if (input.dateFrom > input.dateTo) {
            throw ApiException.invalid(fromField, "INVALID_DATE_RANGE", "Начало периода позже окончания")
        }

        if (input.dateTo > serverDate) {
            throw ApiException.invalid(untilField, "FUTURE_DATE", "Дата не должна быть в будущем")
        }

        val selected = regions(input.regionCodes, regionField)
        val fuel = fuels().singleOrNull { it.code == input.fuelType }
            ?: throw ApiException.invalid(if (json) "selection.fuelType" else "filterFuelType", "UNKNOWN_FUEL_TYPE", "Топливо не найдено")
        val base = selected.single { it.code == input.baseRegionCode }

        return PriceSelection(
            base,
            listOf(base) + selected.filter { it != base }.sortedBy { it.code },
            fuel,
            input.dateFrom,
            input.dateTo,
            input.frequency,
            input.preferredSource
        )
    }

    fun regions(
        codes: List<String>,
        field: String
    ): List<RegionRefDto> {
        if (codes.size !in 1..4 || codes.any { !CODE.matches(it) }) {
            throw ApiException.invalid(field, "INVALID_FORMAT", "Выберите от одного до четырёх корректных кодов регионов")
        }

        if (codes.distinct().size != codes.size) {
            throw ApiException.invalid(field, "DUPLICATE_REGION", "Регионы не должны повторяться")
        }

        val found = regions.findSelected(codes).map { RegionRefDto(it.code, it.name) }

        if (found.size != codes.size) {
            throw ApiException.invalid(field, "UNKNOWN_REGION", "Неизвестный код региона")
        }

        return found
    }

    fun fuels(): List<FuelTypeRefDto> =
        fuels.findOrdered().map { FuelTypeRefDto(FuelType.valueOf(it.code), it.name) }

    fun csv(
        value: String,
        field: String
    ): List<String> {
        val parts = value.split(',')

        if (parts.any { it.isEmpty() || it != it.trim() }) {
            throw ApiException.invalid(field, "INVALID_FORMAT", "Требуется CSV без пустых элементов и пробелов")
        }

        return parts
    }

    companion object {
        private val CODE = Regex("^[A-Z0-9][A-Z0-9_-]{0,31}$")
    }
}
