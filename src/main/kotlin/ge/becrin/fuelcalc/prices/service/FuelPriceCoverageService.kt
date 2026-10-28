package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.dto.CoverageDto
import ge.becrin.fuelcalc.prices.dto.CoverageListDto
import ge.becrin.fuelcalc.prices.dto.CoverageQueryDto
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.sources.service.SourceUpdateReader
import org.springframework.stereotype.Service

@Service
class FuelPriceCoverageService(
    private val archive: ArchiveReadService,

    private val validator: PriceSelectionValidator,

    private val query: PriceQueryExecutor,

    private val policy: SourceFallbackPolicy,

    private val states: SourceUpdateReader
) {
    fun get(input: CoverageQueryDto): CoverageListDto =
        archive.read(input.dataVersion) { meta ->
            val regions = validator.regions(validator.csv(checkNotNull(input.filterRegionCode), "filterRegionCode"), "filterRegionCode")
            val allFuels = validator.fuels()
            val codes = input.filterFuelType?.let { validator.csv(it, "filterFuelType") }

            if (codes != null &&
                (codes.size !in 1..3 || codes.distinct().size != codes.size ||
                    codes.any { code -> allFuels.none { it.code.name == code } })
            ) {
                throw ApiException.invalid("filterFuelType", "UNKNOWN_FUEL_TYPE", "Некорректный список топлива")
            }

            val fuels = allFuels.filter { codes == null || it.code.name in codes }
            val preferred = checkNotNull(input.preferredSource)
            val bounds = query.bounds(regions.map { it.code }, fuels.map { it.code }, PriceFrequency.entries, preferred)
                .associateBy { Triple(it.regionCode, it.fuel, it.frequency) }
            val updates = states.read()
            val data = regions.flatMap { region ->
                fuels.flatMap { fuel ->
                    PriceFrequency.entries.map { frequency ->
                        val row = bounds[Triple(region.code, fuel.code, frequency)]

                        CoverageDto(
                            region,
                            fuel,
                            frequency,
                            row?.first,
                            row?.last,
                            row?.count ?: 0,
                            row?.latestProvider
                        )
                    }
                }
            }

            CoverageListDto(meta, PriceBasis.REGIONAL_CONSUMER_AVERAGE, preferred, policy.order(preferred), data, updates)
        }
}
