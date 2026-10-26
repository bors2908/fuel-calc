package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.fuels.jpa.FuelTypeEntity
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.sources.jpa.DataSourceEntity
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

@Entity(name = "RegionalPriceKey")
@Table(name = "regional_price_keys")
class RegionalPriceKeyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false, updatable = false)
    var region: RegionEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_type_code", nullable = false, updatable = false)
    var fuelType: FuelTypeEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id", nullable = false, updatable = false)
    var period: PricePeriodEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_code", nullable = false, updatable = false)
    var provider: DataSourceEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_observation_id")
    var currentObservation: RegionalPriceObservationEntity? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant
)
