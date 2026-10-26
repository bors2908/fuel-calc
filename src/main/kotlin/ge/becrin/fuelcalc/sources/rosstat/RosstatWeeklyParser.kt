package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class RosstatWeeklyParser(
    private val regions: RegionCatalog
) {
    fun parse(
        document: Document,
        url: String,
        publicationDate: LocalDate?
    ): PreparedPublication {
        val title = document.title().ifBlank { document.selectFirst("h1")?.text().orEmpty() }
        val period = period(title)

        val table = document.select("table").singleOrNull { table ->
            val text = table.text()
            text.contains("АИ-92") && text.contains("АИ-95") && table.select("tr").any { row ->
                val cells = row.select("td, th")

                cells.size == 6 && regions.byName(cells[0].text()) != null
            }
        } ?: throw SourceImportException("UNKNOWN_FORMAT", "Regional weekly price table was not found")
        val facts = mutableListOf<RegionalPriceFact>()

        for (row in table.select("tr")) {
            val cells = row.select("td, th").map { it.text().replace('\u00a0', ' ').trim() }

            if (cells.size != 6 || cells[0].isBlank() || cells[0].contains("федеральный округ") ||
                cells[0].startsWith("Российская Федерация")
            ) {
                continue
            }

            val region = regions.byName(cells[0])
                ?: throw SourceImportException("UNKNOWN_REGION", "Unmapped regional bulletin row")

            for ((index, fuel) in listOf(2 to "AI_92", 3 to "AI_95", 5 to "DIESEL")) {
                price(cells[index])?.let { facts.add(RegionalPriceFact(region.code, fuel, it, publicationDate)) }
            }
        }

        if (facts.isEmpty()) {
            throw SourceImportException("EMPTY_PUBLICATION", "Weekly bulletin has no valid regional facts")
        }

        return PreparedPublication(
            period,
            "ROSSTAT:WEEKLY:${period.referenceDate}", url, "Росстат · средние потребительские цены, ₽/л", facts
        )
    }

    fun period(title: String): PricePeriod {
        val match = Regex("с[о]? (\\d{1,2}) (?:([а-я]+) )?по (\\d{1,2}) ([а-я]+) (\\d{4}) года").find(title)
            ?: throw SourceImportException("UNKNOWN_PERIOD", "Weekly bulletin has no explicit observation interval")
        val (first, firstMonth, last, month, year) = match.destructured
        val end = LocalDate.parse("$last $month $year", DATE_FORMAT)
        var start = LocalDate.parse("$first ${firstMonth.ifBlank { month }} $year", DATE_FORMAT)

        if (start.isAfter(end)) {
            start = start.minusYears(1)
        }

        return PricePeriod(PriceFrequency.WEEKLY, end, start, end)
    }

    fun price(text: String): BigDecimal? {
        val value = text.replace(" ", "").replace(',', '.').trim()

        if (value in setOf("", "-", "–", "—", "...", "…")) {
            return null
        }

        return value.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it.toPlainString().length <= 64 }
            ?: throw SourceImportException("INVALID_PRICE", "Invalid Rosstat price cell")
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.of("ru"))
    }
}
