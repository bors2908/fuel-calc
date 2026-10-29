package ge.becrin.fuelcalc.sources.benzup

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface BenzupDailyPriceRepository : JpaRepository<BenzupDailyPriceEntity, Long> {
    @Query("SELECT MIN(price.observedDate) FROM BenzupDailyPrice price")
    fun firstDate(): LocalDate?

    @Query(
        """
        SELECT price
        FROM BenzupDailyPrice price
            JOIN FETCH price.region
            JOIN FETCH price.fuelType
        WHERE price.observedDate = :date
        ORDER BY price.region.code, price.fuelType.code
        """
    )
    fun findForDate(@Param("date") date: LocalDate): List<BenzupDailyPriceEntity>

    @Query(
        """
        SELECT price
        FROM BenzupDailyPrice price
            JOIN FETCH price.region
            JOIN FETCH price.fuelType
        WHERE price.observedDate BETWEEN :from AND :until
        ORDER BY price.observedDate, price.region.code, price.fuelType.code
        """
    )
    fun findHistory(
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate
    ): List<BenzupDailyPriceEntity>
}
