package ge.becrin.fuelcalc.sources.benzup

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BenzupMeasurementRepository : JpaRepository<BenzupMeasurementEntity, Long> {
    @Query("SELECT sample FROM BenzupMeasurement sample WHERE sample.importRun.id = :runId")
    fun findForRun(@Param("runId") runId: Long): List<BenzupMeasurementEntity>
}
