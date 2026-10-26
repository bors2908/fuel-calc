package ge.becrin.fuelcalc.sources.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Immutable
@Entity(name = "SourcePublication")
@Table(name = "source_publications")
class SourcePublicationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false, updatable = false)
    var feed: SourceFeedEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_import_run_id", nullable = false, updatable = false)
    var firstImportRun: ImportRunEntity,

    @Column(name = "external_key", length = 512, nullable = false, updatable = false)
    var externalKey: String,

    @Column(name = "external_publication_id", length = 512, updatable = false)
    var externalPublicationId: String? = null,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", length = 64, nullable = false, updatable = false)
    var contentHash: String,

    @Column(name = "publication_date", updatable = false)
    var publicationDate: LocalDate? = null,

    @Column(name = "published_at", updatable = false)
    var publishedAt: Instant? = null,

    @Column(name = "loaded_at", nullable = false, updatable = false)
    var loadedAt: Instant,

    @Column(name = "publication_url", updatable = false, columnDefinition = "text")
    var publicationUrl: String? = null,

    @Column(name = "attribution", nullable = false, updatable = false, columnDefinition = "text")
    var attribution: String,

    @Column(name = "methodology_url", updatable = false, columnDefinition = "text")
    var methodologyUrl: String? = null,

    @Column(name = "note", updatable = false, columnDefinition = "text")
    var note: String? = null
)
