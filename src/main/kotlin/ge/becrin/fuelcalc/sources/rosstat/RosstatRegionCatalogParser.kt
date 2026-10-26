package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.sources.service.RegionCatalog
import ge.becrin.fuelcalc.sources.service.SourceImportException
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.openxml4j.opc.PackagePart
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.util.CellReference
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.apache.poi.xssf.usermodel.XSSFComment
import org.springframework.stereotype.Component
import org.xml.sax.InputSource
import java.nio.file.Path
import java.time.YearMonth
import java.util.Locale

@Component
class RosstatRegionCatalogParser {
    private class SheetHandler : XSSFSheetXMLHandler.SheetContentsHandler {
        val regions = linkedMapOf<String, RegionCatalog.Region>()

        private val cells = mutableMapOf<Int, String>()

        private var headersConfirmed = false

        override fun startRow(rowNum: Int) {
            if (rowNum > 20000) {
                throw SourceImportException("ROW_LIMIT", "Region catalog exceeds row limit")
            }

            cells.clear()
        }

        override fun cell(
            cellReference: String?,
            formattedValue: String?,
            comment: XSSFComment?
        ) {
            val reference = cellReference ?: return
            val column = CellReference(reference).col.toInt()

            if (column <= 1) {
                cells[column] = formattedValue.orEmpty().replace('\u00a0', ' ').trim()
            }
        }

        override fun endRow(rowNum: Int) {
            if (cells[0] == "Код территории" && cells[1] == "Наименование территории") {
                headersConfirmed = true

                return
            }

            val code = cells[0]?.takeIf { it.matches(Regex("\\d{10,11}")) } ?: return
            val name = cells[1]?.takeIf { REGION_TYPE.containsMatchIn(it) } ?: return

            require(headersConfirmed) { "Region catalog headers were not verified" }

            val region = RegionCatalog.Region(code.padStart(11, '0'), name)
            val previous = regions.putIfAbsent(region.code, region)

            require(previous == null || previous == region) { "Conflicting region catalog rows" }
        }
    }

    fun parse(path: Path): List<RegionCatalog.Region> {
        OPCPackage.open(path.toFile(), PackageAccess.READ).use { pkg ->
            val reader = XSSFReader(pkg)
            val sheets = reader.sheetsData as XSSFReader.SheetIterator
            var latest: YearMonth? = null
            var selected: PackagePart? = null

            while (sheets.hasNext()) {
                sheets.next().use {
                    val match = Regex("(\\d{2})\\((\\d{4})\\)").matchEntire(sheets.sheetName)

                    if (match != null) {
                        val month = YearMonth.of(match.groupValues[2].toInt(), match.groupValues[1].toInt())

                        if (latest == null || month > latest) {
                            latest = month
                            selected = sheets.sheetPart
                        }
                    }
                }
            }

            val part = selected ?: throw SourceImportException("UNKNOWN_FORMAT", "No monthly sheets in region catalog")
            val handler = SheetHandler()
            val parser = XMLHelper.newXMLReader()
            parser.contentHandler = XSSFSheetXMLHandler(
                reader.stylesTable, null, reader.sharedStringsTable, handler, DataFormatter(Locale.US), false
            )

            part.inputStream.use { parser.parse(InputSource(it)) }

            if (handler.regions.isEmpty()) {
                throw SourceImportException("EMPTY_CATALOG", "Rosstat workbook contains no regional territories")
            }

            return handler.regions.values.toList()
        }
    }

    companion object {
        private val REGION_TYPE = Regex(
            "республика|область|край|автономн[а-я]+ округ|федерального значения", RegexOption.IGNORE_CASE
        )
    }
}
