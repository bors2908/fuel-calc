package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.fuels.FuelType
import ge.becrin.fuelcalc.prices.PriceFrequency
import ge.becrin.fuelcalc.regions.jpa.RegionEntity
import ge.becrin.fuelcalc.sources.PriceSource
import ge.becrin.fuelcalc.sources.SourceProvider
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
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity(name = "FuelPrice")
@Table(name = "fuel_prices")
class FuelPriceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false, updatable = false)
    var region: RegionEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel", length = 16, nullable = false, updatable = false)
    var fuel: FuelType,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false, updatable = false)
    var source: PriceSource,

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 16, nullable = false, updatable = false)
    var frequency: PriceFrequency,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 16, nullable = false)
    var provider: SourceProvider,

    @Column(name = "reference_date", nullable = false, updatable = false)
    var referenceDate: LocalDate,

    @Column(name = "price_rub_per_liter", nullable = false, columnDefinition = "numeric")
    var priceRubPerLiter: BigDecimal,

    @Column(name = "publication_date")
    var publicationDate: LocalDate? = null,

    @Column(name = "loaded_at", nullable = false)
    var loadedAt: Instant,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)
