package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.prices.PriceSeriesStatus
import ge.becrin.fuelcalc.prices.dto.RegionPriceSeriesDto
import ge.becrin.fuelcalc.prices.dto.TrendPointDto
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.model.SourcePriceRows
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import org.springframework.stereotype.Service

@Service
class FuelPriceTrendService {
    fun build(
        selection: PriceSelection,
        rows: SourcePriceRows
    ): List<RegionPriceSeriesDto> {
        val dates = rows.dates

        return selection.regions.map { region ->
            val available = rows.byRegion[region.okatoCode].orEmpty().associateBy { it.period.referenceDate }
            val points = dates.map { date ->
                val row = available[date]

                TrendPointDto(date, row?.price?.toPlainString(), row?.id?.toString(),
                    if (row == null) MissingPriceReason.NO_OBSERVATION_FOR_PERIOD else null,
                    row?.forecast, if (row?.forecast != null) "FORECAST" else "OBSERVED")
            }
            val status = when {
                available.isEmpty() -> PriceSeriesStatus.NO_DATA
                available.size == dates.size -> PriceSeriesStatus.AVAILABLE
                else -> PriceSeriesStatus.PARTIAL
            }

            RegionPriceSeriesDto(RegionRefDto(region), status, points)
        }
    }
}
