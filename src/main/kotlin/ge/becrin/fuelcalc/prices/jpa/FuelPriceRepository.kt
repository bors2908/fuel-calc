package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.sources.PriceSource
import java.time.LocalDate
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FuelPriceRepository : JpaRepository<FuelPriceEntity, Long> {
    @Query("""
        SELECT price FROM FuelPrice price JOIN FETCH price.region
        WHERE price.source = :source AND price.referenceDate BETWEEN :from AND :until
        ORDER BY price.referenceDate, price.region.okatoCode, price.fuel
    """)
    fun findWindow(
        @Param("source") source: PriceSource,
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate
    ): List<FuelPriceEntity>

    @Modifying
    @Query("DELETE FROM FuelPrice price WHERE price.region.okatoCode NOT IN :codes")
    fun deleteOutsideCatalog(@Param("codes") codes: Collection<String>): Int

    @Query("""
        SELECT price FROM FuelPrice price JOIN FETCH price.region region
        WHERE region.okatoCode IN :regions AND price.fuel = :fuel
          AND (price.referenceDate BETWEEN :from AND :until
            OR (price.source = :weeklySource AND price.referenceDate = (
              SELECT MAX(edge.referenceDate) FROM FuelPrice edge
              WHERE edge.region = price.region AND edge.fuel = price.fuel AND edge.source = price.source
                AND edge.referenceDate >= :weeklyFrom AND edge.referenceDate < :from
            )))
        ORDER BY price.referenceDate, region.okatoCode, price.source
    """)
    fun findPageRows(
        @Param("regions") regions: List<String>,
        @Param("fuel") fuel: FuelType,
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate,
        @Param("weeklyFrom") weeklyFrom: LocalDate,
        @Param("weeklySource") weeklySource: PriceSource
    ): List<FuelPriceEntity>

    @Query("""
        SELECT price.source AS source, region.okatoCode AS regionCode,
          MIN(price.referenceDate) AS firstDate, MAX(price.referenceDate) AS lastDate
        FROM FuelPrice price JOIN price.region region
        WHERE region.okatoCode IN :regions AND price.fuel = :fuel
        GROUP BY price.source, region.okatoCode
    """)
    fun findBounds(
        @Param("regions") regions: List<String>,
        @Param("fuel") fuel: FuelType
    ): List<PriceBoundsProjection>

    @Query("""
        SELECT price FROM FuelPrice price JOIN FETCH price.region region
        WHERE region.okatoCode IN :regions AND price.fuel = :fuel AND price.source = :source
          AND price.referenceDate BETWEEN :from AND :until
          AND (price.referenceDate = (
            SELECT MIN(edge.referenceDate) FROM FuelPrice edge
            WHERE edge.region = price.region AND edge.fuel = price.fuel AND edge.source = price.source
              AND edge.referenceDate BETWEEN :from AND :until
          ) OR price.referenceDate = (
            SELECT MAX(edge.referenceDate) FROM FuelPrice edge
            WHERE edge.region = price.region AND edge.fuel = price.fuel AND edge.source = price.source
              AND edge.referenceDate BETWEEN :from AND :until
          ))
        ORDER BY price.referenceDate, region.okatoCode
    """)
    fun findBoundaries(
        @Param("regions") regions: List<String>,
        @Param("fuel") fuel: FuelType,
        @Param("source") source: PriceSource,
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate
    ): List<FuelPriceEntity>

    @Query("""
        SELECT price.referenceDate FROM FuelPrice price JOIN price.region region
        WHERE region.okatoCode IN :regions AND price.fuel = :fuel AND price.source = :source
          AND price.referenceDate BETWEEN :from AND :until
        GROUP BY price.referenceDate
        HAVING COUNT(DISTINCT region.id) = :regionCount
        ORDER BY price.referenceDate DESC
    """)
    fun findCommonDates(
        @Param("regions") regions: List<String>,
        @Param("fuel") fuel: FuelType,
        @Param("source") source: PriceSource,
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate,
        @Param("regionCount") regionCount: Long,
        pageable: Pageable
    ): List<LocalDate>

    @Query("""
        SELECT price FROM FuelPrice price JOIN FETCH price.region region
        WHERE region.okatoCode IN :regions AND price.fuel = :fuel AND price.source = :source
          AND price.referenceDate = :date
        ORDER BY region.okatoCode
    """)
    fun findAtDate(
        @Param("regions") regions: List<String>,
        @Param("fuel") fuel: FuelType,
        @Param("source") source: PriceSource,
        @Param("date") date: LocalDate
    ): List<FuelPriceEntity>

    @Query("SELECT price FROM FuelPrice price JOIN FETCH price.region WHERE price.id = :id")
    fun findDetails(@Param("id") id: Long): FuelPriceEntity?

    @Query("""
        SELECT MAX(price.referenceDate) FROM FuelPrice price
        WHERE price.region.okatoCode = :region AND price.fuel = :fuel AND price.source = :source
    """)
    fun findLatestDate(
        @Param("region") region: String,
        @Param("fuel") fuel: FuelType,
        @Param("source") source: PriceSource
    ): LocalDate?

    @Query("""
        SELECT price FROM FuelPrice price JOIN FETCH price.region region
        WHERE price.source = :source
          AND price.referenceDate = (
            SELECT MIN(first.referenceDate) FROM FuelPrice first
            WHERE first.region = price.region AND first.fuel = price.fuel AND first.source = :source
          )
        ORDER BY region.okatoCode, price.fuel
    """)
    fun findFirstObservations(@Param("source") source: PriceSource): List<FuelPriceEntity>
}
