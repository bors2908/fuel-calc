package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.MissingPriceReason
import ge.becrin.fuelcalc.prices.PriceSeriesStatus
import ge.becrin.fuelcalc.prices.dto.FuelPriceQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceTrendDto
import ge.becrin.fuelcalc.prices.dto.RegionPriceSeriesDto
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import org.springframework.stereotype.Service

@Service
class FuelPriceTrendService(
    private val archive: ArchiveReadService,

    private val contexts: PriceReadContextFactory,

    private val query: PriceQueryExecutor,

    private val cells: PriceCellConverter
) {
    fun get(input: FuelPriceQueryDto): PriceTrendDto =
        archive.read(input.dataVersion) { meta ->
            val context = contexts.query(input, meta.serverDate)
            val rows = query.range(context.selection)
            val calendar = rows.map { it.period }.distinctBy { it.key }.sortedWith(compareBy({ it.referenceDate }, { it.key }))
            val byRegion = rows.groupBy { it.region.code }
            val data = context.selection.regions.map { region ->
                val available = byRegion[region.code].orEmpty().associateBy { it.period.key }
                val bounds = context.bounds[region.code]
                val points = calendar.map { period ->
                    available[period.key]?.let { cells.available(it) }
                        ?: cells.missing(MissingPriceReason.NO_OBSERVATION_FOR_PERIOD, period)
                }
                val status =
                    when {
                        available.isEmpty() -> PriceSeriesStatus.NO_DATA
                        available.size == calendar.size -> PriceSeriesStatus.AVAILABLE
                        else -> PriceSeriesStatus.PARTIAL
                    }

                RegionPriceSeriesDto(region, status, bounds?.first, bounds?.last, points)
            }

            PriceTrendDto(meta, context.dto, data, context.updates)
        }
}
