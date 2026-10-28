package ge.becrin.fuelcalc.prices.service

import ge.becrin.fuelcalc.api.dto.ErrorDetailDto
import ge.becrin.fuelcalc.api.error.ApiException
import ge.becrin.fuelcalc.archive.service.ArchiveReadService
import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.prices.dto.PriceObservationDto
import ge.becrin.fuelcalc.prices.dto.PriceObservationResponseDto
import ge.becrin.fuelcalc.prices.jpa.PriceQueryExecutor
import ge.becrin.fuelcalc.sources.service.SourceUpdateReader
import org.springframework.stereotype.Service

@Service
class FuelPriceObservationService(
    private val archive: ArchiveReadService,

    private val query: PriceQueryExecutor,

    private val states: SourceUpdateReader,

    private val cells: PriceCellConverter
) {
    fun get(observationId: String): PriceObservationResponseDto =
        archive.read { meta ->
            if (observationId.length !in 1..128) {
                throw ApiException.invalid("observationId", "INVALID_FORMAT", "Неверная длина ID наблюдения")
            }

            val id = observationId.toLongOrNull()?.takeIf { it > 0 && it.toString() == observationId }
            val row = id?.let(query::details) ?: throw ApiException(
                404,
                "ResourceNotFound",
                "Наблюдение не найдено",
                listOf(
                    ErrorDetailDto("observationId", "OBSERVATION_NOT_FOUND", "Наблюдение не найдено")
                )
            )
            val price = cells.available(row, query.latestForProvider(row), states.read())

            PriceObservationResponseDto(
                meta,
                PriceObservationDto(
                    row.id.toString(),
                    row.region,
                    row.fuel,
                    PriceBasis.REGIONAL_CONSUMER_AVERAGE,
                    "RUB/L",
                    price,
                    row.publicationDate,
                    row.publishedAt,
                    row.loadedAt,
                    row.publicationUrl,
                    row.attribution,
                    row.methodologyUrl,
                    row.note
                )
            )
        }
}
