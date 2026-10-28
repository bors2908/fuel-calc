package ge.becrin.fuelcalc.archive.jpa

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ArchiveStateRepository : JpaRepository<ArchiveStateEntity, Short> {
    @Query("SELECT archive.revision FROM ArchiveState archive WHERE archive.id = 1")
    fun readRevision(): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT archive FROM ArchiveState archive WHERE archive.id = 1")
    fun lockForPublication(): ArchiveStateEntity

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ArchiveState archive SET archive.revision = archive.revision + 1, archive.updatedAt = :now WHERE archive.id = 1")
    fun advanceRevision(@Param("now") now: Instant)
}
