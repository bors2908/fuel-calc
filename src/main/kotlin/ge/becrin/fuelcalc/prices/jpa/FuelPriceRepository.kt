package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.sources.PriceSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

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
}
