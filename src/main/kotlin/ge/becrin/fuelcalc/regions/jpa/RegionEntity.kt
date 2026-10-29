package ge.becrin.fuelcalc.regions.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity(name = "Region")
@Table(name = "regions")
class RegionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "okato_code", length = 32, nullable = false, updatable = false)
    var okatoCode: String,

    @Column(name = "name", length = 256, nullable = false)
    var name: String,

    @Column(name = "has_data", nullable = false)
    var hasData: Boolean = false
)
