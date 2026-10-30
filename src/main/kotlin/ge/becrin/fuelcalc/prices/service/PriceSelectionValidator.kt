package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.dto.PricePageQueryDto
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.regions.jpa.RegionRepository
import ge.becrin.fuelcalc.regions.model.Region
import java.time.LocalDate
import org.springframework.stereotype.Service

@Service
class PriceSelectionValidator(private val repository: RegionRepository) {
    private data class Fields(
        val base: String,

        val regions: String,

        val from: String,

        val until: String
    )

    fun resolve(
        query: PricePageQueryDto,
        serverDate: LocalDate
    ): PriceSelection =
        resolve(
            checkNotNull(query.baseRegionCode),
            csv(checkNotNull(query.filterRegionCode), "filterRegionCode"),
            checkNotNull(query.filterFuelType),
            checkNotNull(query.filterDateFrom),
            checkNotNull(query.filterDateTo),
            serverDate,
            Fields("baseRegionCode", "filterRegionCode", "filterDateFrom", "filterDateTo")
        )

    fun resolveAsOf(
        baseCode: String,
        codes: List<String>,
        fuel: FuelType,
        date: LocalDate,
        serverDate: LocalDate
    ): PriceSelection =
        resolve(
            baseCode,
            codes,
            fuel,
            date,
            date,
            serverDate,
            Fields("selection.baseRegionCode", "selection.regionCodes", "selection.date", "selection.date")
        )

    private fun resolve(
        baseCode: String,
        codes: List<String>,
        fuel: FuelType,
        from: LocalDate,
        until: LocalDate,
        serverDate: LocalDate,
        fields: Fields
    ): PriceSelection {
        if (!CODE.matches(baseCode)) {
            throw ApiException.invalid(fields.base, "INVALID_FORMAT", "Неверный код основного региона")
        }

        if (baseCode !in codes) {
            throw ApiException.invalid(fields.base, "BASE_REGION_NOT_SELECTED", "Основной регион должен входить в выбор")
        }

        if (from > until) {
            throw ApiException.invalid(fields.from, "INVALID_DATE_RANGE", "Начало периода позже окончания")
        }

        if (until > serverDate.plusYears(1)) {
            throw ApiException.invalid(fields.until, "FORECAST_HORIZON_EXCEEDED", "Прогноз доступен максимум на один год вперёд")
        }

        val selected = regions(codes, fields.regions)
        val base = selected.single { it.okatoCode == baseCode }

        return PriceSelection(base, listOf(base) + selected.filter { it != base }.sortedBy { it.okatoCode }, fuel, from, until)
    }

    private fun regions(
        codes: List<String>,
        field: String
    ): List<Region> {
        if (codes.size !in 1..4 || codes.any { !CODE.matches(it) }) {
            throw ApiException.invalid(field, "INVALID_FORMAT", "Выберите от одного до четырёх корректных кодов регионов")
        }

        if (codes.distinct().size != codes.size) {
            throw ApiException.invalid(field, "DUPLICATE_REGION", "Регионы не должны повторяться")
        }

        val found = repository.findSelected(codes).map { Region(it.okatoCode, it.name) }

        if (found.size != codes.size) {
            throw ApiException.invalid(field, "UNKNOWN_REGION", "Неизвестный код региона")
        }

        return found
    }

    private fun csv(
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
