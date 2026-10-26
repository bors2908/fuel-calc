package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.fuels.jpa.FuelTypeEntity
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.sources.jpa.DataSourceEntity
import ge.becrin.fuelcalc.sources.jpa.ImportRunEntity
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
import java.time.LocalDate
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Immutable
@Entity(name = "RegionalPriceSample")
@Table(name = "regional_price_samples")
class RegionalPriceSampleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_code", nullable = false, updatable = false)
    var provider: DataSourceEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false, updatable = false)
    var region: RegionEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_type_code", nullable = false, updatable = false)
    var fuelType: FuelTypeEntity,

    @Column(name = "sample_date", nullable = false, updatable = false)
    var sampleDate: LocalDate,

    @Column(name = "sampling_policy_code", length = 64, nullable = false, updatable = false)
    var samplingPolicyCode: String,

    @Column(name = "price_rub_per_liter", nullable = false, updatable = false, columnDefinition = "numeric")
    var priceRubPerLiter: BigDecimal,

    @Column(name = "station_count", nullable = false, updatable = false)
    var stationCount: Int,

    @Column(name = "collected_at", nullable = false, updatable = false)
    var collectedAt: Instant,

    @Column(name = "oldest_price_at", nullable = false, updatable = false)
    var oldestPriceAt: Instant,

    @Column(name = "newest_price_at", nullable = false, updatable = false)
    var newestPriceAt: Instant,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "first_import_run_id", nullable = false, updatable = false)
    var firstImportRun: ImportRunEntity,

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", length = 64, nullable = false, updatable = false)
    var contentHash: String
)
