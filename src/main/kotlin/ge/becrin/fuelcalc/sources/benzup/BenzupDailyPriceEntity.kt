package ge.becrin.fuelcalc.sources.benzup

import ge.becrin.fuelcalc.fuels.jpa.FuelTypeEntity
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
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

@Entity(name = "BenzupDailyPrice")
@Table(name = "benzup_daily_prices")
class BenzupDailyPriceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    var region: RegionEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuel_type_code", nullable = false)
    var fuelType: FuelTypeEntity,

    @Column(name = "observed_date", nullable = false)
    var observedDate: LocalDate,

    @Column(name = "price_sum", nullable = false, columnDefinition = "numeric")
    var priceSum: BigDecimal,

    @Column(name = "measurement_count", nullable = false)
    var measurementCount: Int,

    @Column(name = "price_rub_per_liter", nullable = false, columnDefinition = "numeric")
    var priceRubPerLiter: BigDecimal,

    @Column(name = "collected_at", nullable = false)
    var collectedAt: Instant
)
