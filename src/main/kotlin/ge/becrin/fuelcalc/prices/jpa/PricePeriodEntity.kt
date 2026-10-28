package ge.becrin.fuelcalc.prices.jpa

import ge.becrin.fuelcalc.prices.PriceBasis
import ge.becrin.fuelcalc.prices.PriceFrequency
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import org.hibernate.annotations.Immutable

@Immutable
@Entity(name = "PricePeriod")
@Table(name = "price_periods")
class PricePeriodEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "period_key", length = 96, nullable = false, updatable = false)
    var periodKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 64, nullable = false, updatable = false)
    var basis: PriceBasis = PriceBasis.REGIONAL_CONSUMER_AVERAGE,

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 16, nullable = false, updatable = false)
    var frequency: PriceFrequency,

    @Column(name = "reference_date", nullable = false, updatable = false)
    var referenceDate: LocalDate,

    @Column(name = "start_date", updatable = false)
    var startDate: LocalDate? = null,

    @Column(name = "end_date", updatable = false)
    var endDate: LocalDate? = null
)
