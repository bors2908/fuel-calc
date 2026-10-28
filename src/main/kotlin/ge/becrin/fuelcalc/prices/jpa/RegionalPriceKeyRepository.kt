package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RegionalPriceKeyRepository : JpaRepository<RegionalPriceKeyEntity, Long> {
    @Query(
        """
        SELECT price
        FROM RegionalPriceKey price
            JOIN FETCH price.region
            JOIN FETCH price.fuelType
            LEFT JOIN FETCH price.currentObservation current
            LEFT JOIN FETCH current.publication
        WHERE price.period = :period
            AND price.provider.code = :provider
            AND price.region IN :regions
        """
    )
    fun findForPublication(
        @Param("period") period: PricePeriodEntity,
        @Param("provider") provider: String,
        @Param("regions") regions: Collection<RegionEntity>
    ): List<RegionalPriceKeyEntity>

    @Query(
        """
        SELECT price
        FROM RegionalPriceKey price
            JOIN FETCH price.period period
            JOIN FETCH price.region
            JOIN FETCH price.fuelType
            JOIN FETCH price.currentObservation
        WHERE price.provider.code = 'ROSSTAT'
            AND period.frequency = ge.becrin.fuelcalc.prices.PriceFrequency.WEEKLY
            AND period.referenceDate IN :dates
        """
    )
    fun findOfficialAtDates(@Param("dates") dates: Collection<LocalDate>): List<RegionalPriceKeyEntity>

    @Query(
        """
        SELECT MAX(price.period.referenceDate)
        FROM RegionalPriceKey price
        WHERE price.provider.code = :provider
            AND price.period.frequency = :frequency
            AND price.currentObservation IS NOT NULL
        """
    )
    fun latestDate(
        @Param("provider") provider: String,
        @Param("frequency") frequency: PriceFrequency
    ): LocalDate?

    @Query(
        """
        SELECT price.currentObservation.id
        FROM RegionalPriceKey price
        WHERE price.region.code = :region
            AND price.period.referenceDate = :date
            AND price.period.frequency = :frequency
            AND price.provider.code = :provider
        """
    )
    fun currentObservationId(
        @Param("region") region: String,
        @Param("date") date: LocalDate,
        @Param("frequency") frequency: PriceFrequency,
        @Param("provider") provider: String
    ): Long?
}
