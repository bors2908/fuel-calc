package ge.becrin.fuelcalc.archive.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity(name = "ArchiveState")
@Table(name = "archive_state")
class ArchiveStateEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: Short = 1,

    @Column(name = "revision", nullable = false)
    var revision: Long = 1,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant
)
