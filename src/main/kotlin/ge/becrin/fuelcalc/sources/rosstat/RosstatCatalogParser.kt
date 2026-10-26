package ge.becrin.fuelcalc.sources.rosstat

import ge.becrin.fuelcalc.prices.PriceFrequency
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import java.net.URI

@Component
class RosstatCatalogParser {
    data class DocumentLink(
        val title: String,

        val uri: URI
    )

    fun links(
        document: Document,
        frequency: PriceFrequency
    ): List<DocumentLink> {
        val prefix =
            if (frequency == PriceFrequency.MONTHLY) "Средние потребительские цены на отдельные виды товаров и услуг"
            else "О потребительских ценах на нефтепродукты с"
        val suffix = if (frequency == PriceFrequency.MONTHLY) ".xlsx" else ".html"

        return document.select("a[href]").mapNotNull { anchor ->
            val title = anchor.closest(".document-list__item")?.selectFirst(".document-list__item-title")?.text()
                ?: anchor.text()
            val url = anchor.absUrl("href")

            if (title.contains(prefix) && url.isNotBlank()) {
                val uri = URI.create(url)

                if (uri.path.endsWith(suffix)) DocumentLink(title, uri) else null
            } else {
                null
            }
        }.distinctBy { it.uri }
    }
}
