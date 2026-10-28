package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.prices.PriceFrequency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface PricePeriodRepository : JpaRepository<PricePeriodEntity, Long> {
    @Query(
        """
        SELECT period
        FROM PricePeriod period
        WHERE period.basis = ge.becrin.fuelcalc.prices.PriceBasis.REGIONAL_CONSUMER_AVERAGE
            AND period.frequency = :frequency
            AND period.referenceDate = :date
        """
    )
    fun findPeriod(
        @Param("frequency") frequency: PriceFrequency,
        @Param("date") date: LocalDate
    ): PricePeriodEntity?

    @Query(
        """
        SELECT period
        FROM PricePeriod period
        WHERE period.frequency = ge.becrin.fuelcalc.prices.PriceFrequency.WEEKLY
            AND period.startDate <= :until
            AND period.endDate >= :from
            AND EXISTS (
                SELECT price.id
                FROM RegionalPriceKey price
                WHERE price.period = period
                    AND price.provider.code = 'ROSSTAT'
                    AND price.currentObservation IS NOT NULL
            )
        ORDER BY period.referenceDate, period.id
        """
    )
    fun findOfficialWeeks(
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate
    ): List<PricePeriodEntity>

    @Query(
        """
        SELECT period
        FROM PricePeriod period
        WHERE period.frequency = :frequency
            AND period.referenceDate <= :today
            AND period.endDate < :today
            AND period.startDate IS NOT NULL
            AND period.endDate >= :firstSample
        ORDER BY period.referenceDate, period.id
        """
    )
    fun findCompleted(
        @Param("frequency") frequency: PriceFrequency,
        @Param("today") today: LocalDate,
        @Param("firstSample") firstSample: LocalDate
    ): List<PricePeriodEntity>
}
