package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.fuels.dto.FuelTypeRefDto
import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.prices.dto.PricePeriodDto
import ge.becrin.fuelcalc.prices.dto.RegionalPriceSampleDto
import ge.becrin.fuelcalc.prices.model.PriceObservationDetails
import ge.becrin.fuelcalc.prices.model.PriceRow
import ge.becrin.fuelcalc.prices.model.PriceSelection
import ge.becrin.fuelcalc.prices.model.SeriesBounds
import ge.becrin.fuelcalc.prices.service.SourceFallbackPolicy
import ge.becrin.fuelcalc.regions.dto.RegionRefDto
import ge.becrin.fuelcalc.sources.PriceAggregationMethod
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.dto.SourceLabelDto
import ge.becrin.fuelcalc.sources.dto.SourceRefDto
import ge.becrin.fuelcalc.sources.dto.SourceSummaryDto
import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import jakarta.persistence.TypedQuery
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Repository
class PriceQueryExecutor(
    private val entityManager: EntityManager,

    private val policy: SourceFallbackPolicy
) {
    fun range(selection: PriceSelection): List<PriceRow> =
        rows(selection, "", "ORDER BY p.referenceDate, p.periodKey, r.code")

    fun boundaries(selection: PriceSelection): List<PriceRow> =
        rows(
            selection,
            """
            AND (
                p.referenceDate = (SELECT MIN(edge.period.referenceDate) $BOUNDARY_FROM)
                OR p.referenceDate = (SELECT MAX(edge.period.referenceDate) $BOUNDARY_FROM)
            )
            """.trimIndent(),
            "ORDER BY p.referenceDate, r.code"
        )

    fun commonPeriod(selection: PriceSelection): Long? =
        query(
            """
            SELECT p.id
            $KEY_FROM
            WHERE $RESOLVED
                AND p.referenceDate BETWEEN :from AND :until
            GROUP BY p.id, p.referenceDate
            HAVING COUNT(DISTINCT r.id) = :regionCount
            ORDER BY p.referenceDate DESC, p.id DESC
            """.trimIndent(),
            Long::class.javaObjectType,
            parameters(selection)
        ).setMaxResults(1).resultList.singleOrNull()

    fun atPeriod(
        selection: PriceSelection,
        periodId: Long
    ): List<PriceRow> =
        rows(selection, "AND p.id = :periodId", "ORDER BY r.code", mapOf("periodId" to periodId))

    fun bounds(
        regions: List<String>,
        fuels: List<FuelType>,
        frequencies: List<PriceFrequency>,
        preferred: PriceSource
    ): List<SeriesBounds> {
        val result = query(
            """
            SELECT r.code AS regionCode, k.fuelType.code AS fuelCode, p.frequency AS frequency,
                MIN(p.referenceDate) AS firstDate, MAX(p.referenceDate) AS lastDate, COUNT(k) AS periodCount,
                MIN(CASE WHEN p.referenceDate = (
                    SELECT MAX(latest.period.referenceDate)
                    FROM RegionalPriceKey latest
                    WHERE latest.region = k.region
                        AND latest.fuelType = k.fuelType
                        AND latest.period.frequency = p.frequency
                        AND latest.period.basis = :basis
                        AND latest.currentObservation IS NOT NULL
                ) THEN provider.code ELSE NULL END) AS latestProvider
            $KEY_FROM
            WHERE $RESOLVED
            GROUP BY r.code, k.region, k.fuelType, k.fuelType.code, p.frequency
            ORDER BY r.code, k.fuelType.code, p.frequency
            """.trimIndent(),
            Tuple::class.java,
            parameters(regions, fuels, frequencies, preferred)
        ).resultList

        return result.map { value ->
            SeriesBounds(
                regionCode = value.get("regionCode", String::class.java),
                fuel = FuelType.valueOf(value.get("fuelCode", String::class.java)),
                frequency = value.get("frequency", PriceFrequency::class.java),
                first = value.get("firstDate", LocalDate::class.java),
                last = value.get("lastDate", LocalDate::class.java),
                count = value.get("periodCount", Number::class.java).toInt(),
                latestProvider = PriceSource.valueOf(value.get("latestProvider", String::class.java))
            )
        }
    }

    fun details(id: Long): PriceObservationDetails? {
        val observation = query(
            """
            $OBSERVATION_FROM
                JOIN FETCH feed.publisher
            WHERE o.id = :id
            """.trimIndent(),
            RegionalPriceObservationEntity::class.java,
            mapOf("id" to id)
        ).resultList.singleOrNull() ?: return null
        val publication = observation.publication
        val feed = publication.feed
        val publisher = feed.publisher

        return PriceObservationDetails(
            price = row(observation),
            publisher = SourceRefDto(publisher.code, publisher.name, publisher.url),
            providerUrl = observation.priceKey.provider.url,
            datasetId = feed.externalCode,
            publicationId = publication.externalPublicationId,
            publicationDate = publication.publicationDate,
            publishedAt = publication.publishedAt,
            loadedAt = publication.loadedAt,
            publicationUrl = publication.publicationUrl,
            attribution = publication.attribution,
            methodologyUrl = publication.methodologyUrl,
            note = publication.note
        )
    }

    fun latestForProvider(row: PriceRow): LocalDate? =
        query(
            """
            SELECT MAX(price.period.referenceDate)
            FROM RegionalPriceKey price
            WHERE price.region.code = :region
                AND price.fuelType.code = :fuel
                AND price.provider.code = :provider
                AND price.period.frequency = :frequency
                AND price.period.basis = :basis
                AND price.currentObservation IS NOT NULL
            """.trimIndent(),
            LocalDate::class.java,
            mapOf(
                "region" to row.region.code,
                "fuel" to row.fuel.code.name,
                "provider" to row.source.provider.code,
                "frequency" to row.period.frequency,
                "basis" to PriceBasis.REGIONAL_CONSUMER_AVERAGE
            )
        ).singleResult

    private fun rows(
        selection: PriceSelection,
        where: String,
        order: String,
        extra: Map<String, Any> = emptyMap()
    ): List<PriceRow> =
        query(
            "$OBSERVATION_FROM WHERE k.currentObservation = o AND $RESOLVED " +
                "AND p.referenceDate BETWEEN :from AND :until $where $order",
            RegionalPriceObservationEntity::class.java,
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
        parameters(selection.regions.map { it.code }, listOf(selection.fuel.code), listOf(selection.frequency), selection.preferred) +
            mapOf("from" to selection.from, "until" to selection.until, "regionCount" to selection.regions.size.toLong())

    private fun parameters(
        regions: List<String>,
        fuels: List<FuelType>,
        frequencies: List<PriceFrequency>,
        preferred: PriceSource
    ): Map<String, Any> {
        val order = policy.order(preferred)

        return mapOf(
            "regions" to regions,
            "fuels" to fuels.map { it.name },
            "frequencies" to frequencies,
            "basis" to PriceBasis.REGIONAL_CONSUMER_AVERAGE,
            "firstProvider" to order[0].name,
            "secondProvider" to order[1].name
        )
    }

    private fun row(observation: RegionalPriceObservationEntity): PriceRow {
        val key = observation.priceKey
        val feed = observation.publication.feed
        val sample =
            if (feed.aggregationMethod == PriceAggregationMethod.STATION_SAMPLE_MEAN) {
                RegionalPriceSampleDto(
                    checkNotNull(feed.samplingPolicyCode),
                    checkNotNull(observation.sampleMinStationCount),
                    checkNotNull(observation.sampleDayCount),
                    checkNotNull(observation.sampleExpectedDayCount),
                    checkNotNull(observation.sampleCollectedAt)
                )
            } else {
                null
            }

        return PriceRow(
            id = observation.id,
            region = RegionRefDto(key.region.code, key.region.name),
            fuel = FuelTypeRefDto(FuelType.valueOf(key.fuelType.code), key.fuelType.name),
            period = period(key.period),
            price = observation.priceRubPerLiter,
            source = SourceSummaryDto(
                provider = SourceLabelDto(key.provider.code, key.provider.name),
                updateKey = feed.code,
                aggregationMethod = feed.aggregationMethod,
                sample = sample
            )
        )
    }

    private fun period(period: PricePeriodEntity): PricePeriodDto {
        val date = period.referenceDate
        val start = period.startDate
        val end = period.endDate
        val label =
            when {
                period.frequency == PriceFrequency.MONTHLY -> date.format(MONTH_LABEL).replaceFirstChar { it.titlecase(Locale.of("ru")) }
                start != null && end != null -> "${start.format(DATE_LABEL)} — ${end.format(DATE_LABEL)}"
                else -> "Неделя на ${date.format(DATE_LABEL)}"
            }

        return PricePeriodDto(period.periodKey, period.frequency, date, label, start, end)
    }

    companion object {
        private val MONTH_LABEL = DateTimeFormatter.ofPattern("LLLL uuuu", Locale.of("ru"))

        private val DATE_LABEL = DateTimeFormatter.ofPattern("dd.MM.uuuu")

        private const val KEY_FROM = """
            FROM RegionalPriceKey k
                JOIN k.region r
                JOIN k.period p
                JOIN k.provider provider
        """

        private const val OBSERVATION_FROM = """
            SELECT o
            FROM RegionalPriceObservation o
                JOIN FETCH o.priceKey k
                JOIN FETCH k.region r
                JOIN FETCH k.fuelType
                JOIN FETCH k.period p
                JOIN FETCH k.provider provider
                JOIN FETCH o.publication publication
                JOIN FETCH publication.feed feed
        """

        private const val RESOLVED = """
            r.code IN :regions
                AND k.fuelType.code IN :fuels
                AND p.frequency IN :frequencies
                AND p.basis = :basis
                AND k.currentObservation IS NOT NULL
                AND NOT EXISTS (
                    SELECT alternative.id
                    FROM RegionalPriceKey alternative
                    WHERE alternative.region = k.region
                        AND alternative.fuelType = k.fuelType
                        AND alternative.period = p
                        AND alternative.currentObservation IS NOT NULL
                        AND CASE alternative.provider.code WHEN :firstProvider THEN 0 WHEN :secondProvider THEN 1 ELSE 2 END
                            < CASE provider.code WHEN :firstProvider THEN 0 WHEN :secondProvider THEN 1 ELSE 2 END
                )
        """

        private const val BOUNDARY_FROM = """
            FROM RegionalPriceKey edge
            WHERE edge.region = k.region
                AND edge.fuelType = k.fuelType
                AND edge.period.frequency = p.frequency
                AND edge.period.basis = :basis
                AND edge.currentObservation IS NOT NULL
                AND edge.period.referenceDate BETWEEN :from AND :until
        """
    }
}
