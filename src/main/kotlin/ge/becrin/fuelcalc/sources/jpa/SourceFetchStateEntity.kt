package ge.becrin.fuelcalc.sources.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity(name = "SourceFetchState")
@Table(name = "source_fetch_states")
class SourceFetchStateEntity(
    @Id
    @Column(name = "feed_id", nullable = false, updatable = false)
    var feedId: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "feed_id", nullable = false, updatable = false)
    var feed: SourceFeedEntity,

    @Column(name = "backfill_next_from")
    var backfillNextFrom: LocalDate? = null,

    @Column(name = "backfill_until")
    var backfillUntil: LocalDate? = null,

    @Column(name = "backfill_completed_at")
    var backfillCompletedAt: Instant? = null,

    @Column(name = "reconciliation_next_from")
    var reconciliationNextFrom: LocalDate? = null,

    @Column(name = "reconciliation_until")
    var reconciliationUntil: LocalDate? = null,

    @Column(name = "last_reconciled_at")
    var lastReconciledAt: Instant? = null,

    @Column(name = "next_fetch_not_before")
    var nextFetchNotBefore: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant
)
