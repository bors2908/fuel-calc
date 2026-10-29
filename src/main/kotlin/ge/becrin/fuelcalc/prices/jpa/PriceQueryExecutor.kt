package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.prices.model.PriceObservationDetails
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.model.SeriesBounds
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.dto.SourceRefDto
import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import jakarta.persistence.TypedQuery
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Repository
class PriceQueryExecutor(private val entityManager: EntityManager) {
    fun range(selection: PriceSelection): List<PriceRow> =
        rows(selection, "", "ORDER BY price.referenceDate, region.okatoCode")

    fun boundaries(selection: PriceSelection): List<PriceRow> =
        rows(
            selection,
            """
            AND (price.referenceDate = (SELECT MIN(edge.referenceDate) $BOUNDARY_FROM)
                OR price.referenceDate = (SELECT MAX(edge.referenceDate) $BOUNDARY_FROM))
            """.trimIndent(),
            "ORDER BY price.referenceDate, region.okatoCode"
        )

    fun commonPeriod(selection: PriceSelection): LocalDate? =
        query(
            """
            SELECT price.referenceDate FROM FuelPrice price JOIN price.region region
            WHERE $SELECTED AND price.referenceDate BETWEEN :from AND :until
            GROUP BY price.referenceDate
            HAVING COUNT(DISTINCT region.id) = :regionCount
            ORDER BY price.referenceDate DESC
            """.trimIndent(),
            LocalDate::class.java,
            parameters(selection) + ("regionCount" to selection.regions.size.toLong())
        ).setMaxResults(1).resultList.singleOrNull()

    fun atPeriod(
        selection: PriceSelection,
        date: LocalDate
    ): List<PriceRow> =
        rows(selection, "AND price.referenceDate = :date", "ORDER BY region.okatoCode", mapOf("date" to date))

    fun bounds(selection: PriceSelection): List<SeriesBounds> =
        query(
            """
            SELECT region.okatoCode AS regionCode, MIN(price.referenceDate) AS firstDate, MAX(price.referenceDate) AS lastDate
            FROM FuelPrice price JOIN price.region region
            WHERE $SELECTED
            GROUP BY region.okatoCode
            ORDER BY region.okatoCode
            """.trimIndent(),
            Tuple::class.java,
            parameters(selection)
        ).resultList.map { value ->
            SeriesBounds(
                value.get("regionCode", String::class.java),
                value.get("firstDate", LocalDate::class.java),
                value.get("lastDate", LocalDate::class.java)
            )
        }

    fun details(id: Long): PriceObservationDetails? {
        val price = query(
            "SELECT price FROM FuelPrice price JOIN FETCH price.region WHERE price.id = :id",
            FuelPriceEntity::class.java,
            mapOf("id" to id)
        ).resultList.singleOrNull() ?: return null
        val provider = price.provider

        return PriceObservationDetails(
            row(price),
            SourceRefDto(provider.name, provider.displayName, provider.url),
            price.publicationDate,
            price.loadedAt
        )
    }

    fun latestForProvider(row: PriceRow): LocalDate? =
        query(
            """
            SELECT MAX(price.referenceDate) FROM FuelPrice price
            WHERE price.region.okatoCode = :region AND price.fuel = :fuel AND price.source = :source
            """.trimIndent(),
            LocalDate::class.java,
            mapOf("region" to row.region.okatoCode, "fuel" to row.fuel.code, "source" to row.source)
        ).singleResult

    private fun rows(
        selection: PriceSelection,
        condition: String,
        order: String,
        extra: Map<String, Any> = emptyMap()
    ): List<PriceRow> =
        query(
            "SELECT price FROM FuelPrice price JOIN FETCH price.region region " +
                "WHERE $SELECTED AND price.referenceDate BETWEEN :from AND :until $condition $order",
            FuelPriceEntity::class.java,
            parameters(selection) + extra
        ).resultList.map(::row)

    private fun <T> query(
        jpql: String,
        type: Class<T>,
        parameters: Map<String, Any>
    ): TypedQuery<T> {
        val query = entityManager.createQuery(jpql, type)
        val names = query.parameters.map { it.name }.toSet()

        for ((name, value) in parameters) {
            if (name in names) {
                query.setParameter(name, value)
            }
        }

        return query
    }

    private fun parameters(selection: PriceSelection): Map<String, Any> =
        mapOf(
            "regions" to selection.regions.map { it.okatoCode },
            "fuel" to selection.fuel.code,
            "source" to selection.source,
            "from" to selection.from,
            "until" to selection.until
        )

    private fun row(price: FuelPriceEntity): PriceRow {
        val date = price.referenceDate
        val frequency = price.frequency
        val daily = frequency == PriceFrequency.DAILY
        val period = PricePeriodDto(
            "$frequency:$date",
            frequency,
            date,
            if (daily) date.format(DATE_LABEL) else "Неделя на ${date.format(DATE_LABEL)}",
            if (daily) date else null,
            if (daily) date else null
        )

        return PriceRow(
            price.id,
            RegionRefDto(price.region.okatoCode, price.region.name),
            FuelTypeRefDto(price.fuel),
            period,
            price.priceRubPerLiter,
            price.source,
            price.version
        )
    }

    companion object {
        private val DATE_LABEL = DateTimeFormatter.ofPattern("dd.MM.uuuu")

        private const val SELECTED = "region.okatoCode IN :regions AND price.fuel = :fuel AND price.source = :source"

        private const val BOUNDARY_FROM = """
            FROM FuelPrice edge
            WHERE edge.region = price.region AND edge.fuel = price.fuel AND edge.source = price.source
                AND edge.referenceDate BETWEEN :from AND :until
        """
    }
}
