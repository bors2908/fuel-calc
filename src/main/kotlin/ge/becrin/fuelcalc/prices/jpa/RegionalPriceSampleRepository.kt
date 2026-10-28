package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RegionalPriceSampleRepository : JpaRepository<RegionalPriceSampleEntity, Long> {
    @Query("SELECT MIN(sample.sampleDate) FROM RegionalPriceSample sample WHERE sample.samplingPolicyCode = :policy")
    fun firstDate(@Param("policy") policy: String): LocalDate?

    @Query(
        """
        SELECT sample
        FROM RegionalPriceSample sample
            JOIN FETCH sample.region
            JOIN FETCH sample.fuelType
        WHERE sample.provider.code = 'WHEREFUEL'
            AND sample.samplingPolicyCode = :policy
            AND sample.region IN :regions
            AND sample.sampleDate IN :dates
            AND NOT EXISTS (
                SELECT newer.id
                FROM RegionalPriceSample newer
                WHERE newer.region = sample.region
                    AND newer.fuelType = sample.fuelType
                    AND newer.sampleDate = sample.sampleDate
                    AND newer.provider = sample.provider
                    AND newer.samplingPolicyCode = sample.samplingPolicyCode
                    AND (newer.collectedAt > sample.collectedAt OR (newer.collectedAt = sample.collectedAt AND newer.id > sample.id))
            )
        ORDER BY sample.region.code, sample.fuelType.code, sample.sampleDate
        """
    )
    fun findLatestForDates(
        @Param("regions") regions: Collection<RegionEntity>,
        @Param("dates") dates: Collection<LocalDate>,
        @Param("policy") policy: String
    ): List<RegionalPriceSampleEntity>

    @Query(
        """
        SELECT sample
        FROM RegionalPriceSample sample
            JOIN FETCH sample.region
            JOIN FETCH sample.fuelType
        WHERE sample.provider.code = 'WHEREFUEL'
            AND sample.samplingPolicyCode = :policy
            AND sample.sampleDate BETWEEN :from AND :until
            AND NOT EXISTS (
                SELECT newer.id
                FROM RegionalPriceSample newer
                WHERE newer.region = sample.region
                    AND newer.fuelType = sample.fuelType
                    AND newer.sampleDate = sample.sampleDate
                    AND newer.provider = sample.provider
                    AND newer.samplingPolicyCode = sample.samplingPolicyCode
                    AND (newer.collectedAt > sample.collectedAt OR (newer.collectedAt = sample.collectedAt AND newer.id > sample.id))
            )
        ORDER BY sample.region.code, sample.fuelType.code, sample.sampleDate
        """
    )
    fun findLatestInRange(
        @Param("from") from: LocalDate,
        @Param("until") until: LocalDate,
        @Param("policy") policy: String
    ): List<RegionalPriceSampleEntity>
}
