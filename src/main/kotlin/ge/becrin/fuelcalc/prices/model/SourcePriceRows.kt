package ge.becrin.fuelcalc.prices.model


class SourcePriceRows(rows: List<PriceRow>) {
    val byRegion: Map<String, List<PriceRow>> =
        rows.groupBy { it.region.okatoCode }.mapValues { (_, prices) -> prices.sortedBy { it.period.referenceDate } }

    val byDate = rows.groupBy { it.period.referenceDate }

    val dates = byDate.keys.sorted()

    val latest = byRegion.values.map { it.last() }
}
