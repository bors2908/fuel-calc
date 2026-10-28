package ge.becrin.fuelcalc.sources.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ImportRunRepository : JpaRepository<ImportRunEntity, Long> {
    @Query(
        """
        SELECT run
        FROM ImportRun run
            JOIN FETCH run.feed
        WHERE run.id = :id
        """
    )
    fun findWithFeed(@Param("id") id: Long): ImportRunEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE ImportRun run
        SET run.status = ge.becrin.fuelcalc.sources.ImportRunStatus.FAILED,
            run.finishedAt = :now,
            run.errorCode = 'INTERRUPTED',
            run.errorMessage = 'Application restarted'
        WHERE run.status = ge.becrin.fuelcalc.sources.ImportRunStatus.RUNNING
        """
    )
    fun interruptRunning(@Param("now") now: Instant)
}
