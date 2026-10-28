package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.sources.ImportRunStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity(name = "ImportRun")
@Table(name = "import_runs")
class ImportRunEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false, updatable = false)
    var feed: SourceFeedEntity,

    @Column(name = "started_at", nullable = false, updatable = false)
    var startedAt: Instant,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    var status: ImportRunStatus = ImportRunStatus.RUNNING,

    @Column(name = "imported_count", nullable = false)
    var importedCount: Int = 0,

    @Column(name = "error_code", length = 64)
    var errorCode: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null
)
