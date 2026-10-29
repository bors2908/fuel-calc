package ge.becrin.fuelcalc.sources.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity(name = "ImportState")
@Table(name = "import_states")
class ImportStateEntity(
    @Id
    @Column(name = "code", length = 32)
    var code: String,

    @Column(name = "content_hash", length = 64)
    var contentHash: String? = null,

    @Column(name = "fetched_through")
    var fetchedThrough: LocalDate? = null,

    @Column(name = "last_success_at")
    var lastSuccessAt: Instant? = null,

    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null,

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null
)
