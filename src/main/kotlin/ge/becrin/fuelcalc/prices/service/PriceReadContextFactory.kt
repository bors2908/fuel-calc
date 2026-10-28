package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.prices.dto.FuelPriceQueryDto
import ge.becrin.fuelcalc.prices.dto.PriceContextDto
import ge.becrin.fuelcalc.prices.dto.PriceSelectionDto
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.model.SeriesBounds
import ge.becrin.fuelcalc.sources.service.SourceUpdateReader
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PriceReadContextFactory(
    private val validator: PriceSelectionValidator,

    private val query: PriceQueryExecutor,

    private val states: SourceUpdateReader,

    private val identity: PriceIdentityFactory
) {
    data class ReadContext(
        val selection: PriceSelection,

        val dto: PriceContextDto,

        val bounds: Map<String, SeriesBounds>,

        val updates: SourceUpdateReader.Updates
    )

    fun query(
        input: FuelPriceQueryDto,
        date: LocalDate
    ): ReadContext =
        prepare(validator.resolve(input, date))

    fun selection(
        input: PriceSelectionDto,
        date: LocalDate
    ): ReadContext =
        prepare(validator.resolve(input, date))

    private fun prepare(selection: PriceSelection): ReadContext {
        val bounds = query.bounds(
            selection.regions.map { it.code },
            listOf(selection.fuel.code),
            listOf(selection.frequency),
            selection.preferred
        ).associateBy { it.regionCode }

        return ReadContext(selection, identity.context(selection), bounds, states.read())
    }
}
