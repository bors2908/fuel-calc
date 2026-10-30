package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.prices.model.PriceObservationDetails
import ge.becrin.fuelcalc.prices.model.PricePeriod
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.model.SeriesBounds
import ge.becrin.fuelcalc.regions.model.Region
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class PriceQueryExecutor(private val prices: FuelPriceRepository) {
    fun pageRows(selection: PriceSelection): List<PriceRow> =
        prices.findPageRows(
            regions = codes(selection),
            fuel = selection.fuel,
            from = selection.from,
            until = selection.until,
            weeklyFrom = selection.from.minusDays(6),
            weeklySource = PriceSource.OFFICIAL
        ).map(::row)

    fun pageBounds(selection: PriceSelection): Map<PriceSource, Map<String, SeriesBounds>> =
        prices.findBounds(codes(selection), selection.fuel).groupBy { it.source }.mapValues { (_, rows) ->
            rows.associate { value -> value.regionCode to SeriesBounds(value.regionCode, value.firstDate, value.lastDate) }
        }

    fun boundaries(
        selection: PriceSelection,
        source: PriceSource
    ): List<PriceRow> =
        prices.findBoundaries(codes(selection), selection.fuel, source, selection.from, selection.until).map(::row)

    fun commonPeriod(
        selection: PriceSelection,
        source: PriceSource
    ): LocalDate? =
        prices.findCommonDates(
            regions = codes(selection),
            fuel = selection.fuel,
            source = source,
            from = selection.from,
            until = selection.until,
            regionCount = selection.regions.size.toLong(),
            pageable = PageRequest.of(0, 1)
        ).singleOrNull()

    fun atPeriod(
        selection: PriceSelection,
        source: PriceSource,
        date: LocalDate
    ): List<PriceRow> =
        prices.findAtDate(codes(selection), selection.fuel, source, date).map(::row)

    fun details(id: Long): PriceObservationDetails? =
        prices.findDetails(id)?.let { price ->
            PriceObservationDetails(row(price), price.provider, price.publicationDate, price.loadedAt)
        }

    fun latestForSource(row: PriceRow): LocalDate? =
        prices.findLatestDate(row.region.okatoCode, row.fuel, row.source)

    private fun codes(selection: PriceSelection): List<String> =
        selection.regions.map { it.okatoCode }

    private fun row(price: FuelPriceEntity): PriceRow =
        PriceRow(
            price.id,
            Region(price.region.okatoCode, price.region.name),
            price.fuel,
            PricePeriod(price.frequency, price.referenceDate),
            price.priceRubPerLiter,
            price.source
        )
}
