package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.sources.jpa.SourcePublicationEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import org.hibernate.annotations.Immutable

@Immutable
@Entity(name = "RegionalPriceObservation")
@Table(name = "regional_price_observations")
class RegionalPriceObservationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_key_id", nullable = false, updatable = false)
    var priceKey: RegionalPriceKeyEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false, updatable = false)
    var publication: SourcePublicationEntity,

    @Column(name = "price_rub_per_liter", nullable = false, updatable = false, columnDefinition = "numeric")
    var priceRubPerLiter: BigDecimal,

    @Column(name = "sample_min_station_count", updatable = false)
    var sampleMinStationCount: Int? = null,

    @Column(name = "sample_day_count", updatable = false)
    var sampleDayCount: Int? = null,

    @Column(name = "sample_expected_day_count", updatable = false)
    var sampleExpectedDayCount: Int? = null,

    @Column(name = "sample_collected_at", updatable = false)
    var sampleCollectedAt: Instant? = null
)
