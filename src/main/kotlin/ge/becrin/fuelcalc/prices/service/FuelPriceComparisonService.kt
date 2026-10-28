package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.dto.FuelPriceComparisonQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceComparisonDto
import org.springframework.stereotype.Service

@Service
class FuelPriceComparisonService(
    private val archive: ArchiveReadService,

    private val contexts: PriceReadContextFactory,

    private val resolver: ComparisonResolver,

    private val converter: ComparisonConverter
) {
    fun get(input: FuelPriceComparisonQueryDto): PriceComparisonDto =
        archive.read(input.dataVersion) { meta ->
            val context = contexts.query(input, meta.serverDate)
            val snapshot = resolver.resolve(context.selection)

            PriceComparisonDto(meta, context.dto, converter.toDto(snapshot, context, input.sortBy, input.sortOrder), context.updates)
        }
}
