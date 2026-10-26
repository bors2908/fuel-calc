package ge.becrin.fuelcalc.sources.jpa

import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.sources.PriceAggregationMethod
import ge.becrin.fuelcalc.sources.SourceUpdateStatus
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

@Entity(name = "SourceFeed")
@Table(name = "source_feeds")
class SourceFeedEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "code", length = 64, nullable = false, updatable = false)
    var code: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_code", nullable = false, updatable = false)
    var publisher: DataSourceEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_code", nullable = false, updatable = false)
    var provider: DataSourceEntity,

    @Column(name = "external_code", length = 256, nullable = false, updatable = false)
    var externalCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 16, nullable = false, updatable = false)
    var frequency: PriceFrequency,

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation_method", length = 32, nullable = false, updatable = false)
    var aggregationMethod: PriceAggregationMethod,

    @Column(name = "sampling_policy_code", length = 64, updatable = false)
    var samplingPolicyCode: String? = null,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Enumerated(EnumType.STRING)
    @Column(name = "update_status", length = 16, nullable = false)
    var updateStatus: SourceUpdateStatus = SourceUpdateStatus.UNKNOWN,

    @Column(name = "last_success_at")
    var lastSuccessAt: Instant? = null,

    @Column(name = "next_expected_publication_at")
    var nextExpectedPublicationAt: Instant? = null,

    @Column(name = "update_message", columnDefinition = "text")
    var updateMessage: String? = null
)
