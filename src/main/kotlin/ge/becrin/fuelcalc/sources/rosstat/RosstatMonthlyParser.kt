package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.model.PreparedPublication
import ge.becrin.fuelcalc.sources.model.PricePeriod
import ge.becrin.fuelcalc.sources.model.RegionalPriceFact
import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.util.CellReference
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.apache.poi.xssf.usermodel.XSSFComment
import org.springframework.stereotype.Component
import org.xml.sax.InputSource
import java.nio.file.Path
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

@Component
class RosstatMonthlyParser(
    private val regions: RegionCatalog,

    private val prices: RosstatWeeklyParser
) {
    private inner class SheetHandler(
        private val date: LocalDate,

        private val publicationDate: LocalDate?
    ) : XSSFSheetXMLHandler.SheetContentsHandler {
        val facts = mutableListOf<RegionalPriceFact>()

        private val cells = mutableMapOf<Int, String>()

        private var columns = emptyMap<Int, String>()

        private var headersConfirmed = false

        override fun startRow(rowNum: Int) {
            if (rowNum > 20000) {
                throw SourceImportException("ROW_LIMIT", "Monthly sheet exceeds row limit")
            }

            cells.clear()
        }

        override fun cell(
            cellReference: String?,
            formattedValue: String?,
            comment: XSSFComment?
        ) {
            val reference = cellReference ?: return
            cells[CellReference(reference).col.toInt()] = formattedValue.orEmpty()
        }

        override fun endRow(rowNum: Int) {
            if (cells[0] == "Код территории" && cells[1] == "Наименование территории") {
                columns = cells.mapNotNull { (index, value) -> FUELS[value]?.let { index to it } }.toMap()
                require(columns.values.toSet() == FUELS.values.toSet()) { "Missing monthly fuel columns" }

                return
            }

            if (columns.isNotEmpty() && cells.values.any { it.contains("Дизельное топливо") }) {
                headersConfirmed = columns.all { (index, fuel) ->
                    val label = cells[index].orEmpty()
                    label.contains("л") &&
                        when (fuel) {
                            "AI_92" -> label.contains("АИ-92")
                            "AI_95" -> label.contains("АИ-95")
                            else -> label.contains("Дизельное топливо")
                        }
                }

                return
            }

            val code = cells[0]?.trim()?.takeIf { it.matches(Regex("\\d{10,11}")) } ?: return
            val region = regions.byCode(code) ?: return
            require(headersConfirmed) { "Monthly fuel units were not verified" }
            require(regions.byName(cells[1].orEmpty())?.code == region.code) { "Region code/name mismatch" }

            for ((index, fuel) in columns) {
                prices.price(cells[index].orEmpty())?.let { facts.add(RegionalPriceFact(region.code, fuel, it, publicationDate)) }
            }
        }
    }

    fun parse(
        path: Path,
        url: String,
        publicationDate: LocalDate?
    ): List<PreparedPublication> {
        val result = mutableListOf<PreparedPublication>()

        OPCPackage.open(path.toFile(), PackageAccess.READ).use { pkg ->
            val reader = XSSFReader(pkg)
            val sheets = reader.sheetsData as XSSFReader.SheetIterator

            while (sheets.hasNext()) {
                sheets.next().use { stream ->
                    val match = Regex("(\\d{2})\\((\\d{4})\\)").matchEntire(sheets.sheetName)

                    if (match != null) {
                        val month = YearMonth.of(match.groupValues[2].toInt(), match.groupValues[1].toInt())
                        val handler = SheetHandler(month.atEndOfMonth(), publicationDate)
                        val parser = XMLHelper.newXMLReader()
                        parser.contentHandler = XSSFSheetXMLHandler(
                            reader.stylesTable, null, reader.sharedStringsTable, handler, DataFormatter(Locale.US), false
                        )
                        parser.parse(InputSource(stream))

                        if (handler.facts.isEmpty()) {
                            throw SourceImportException("EMPTY_PUBLICATION", "Monthly sheet has no regional fuel facts")
                        }

                        result.add(PreparedPublication(
                            PricePeriod(PriceFrequency.MONTHLY, month.atEndOfMonth(), month.atDay(1), month.atEndOfMonth()),
                            "ROSSTAT:MONTHLY:$month", url, "Росстат · средние потребительские цены, ₽/л", handler.facts
                        ))
                    }
                }
            }
        }

        require(result.isNotEmpty()) { "No monthly sheets found" }

        return result
    }

    companion object {
        private val FUELS = mapOf("7802" to "AI_92", "7803" to "AI_95", "7804" to "DIESEL")
    }
}
