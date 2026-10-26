package ge.becrin.fuelcalc.regions.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity(name = "Region")
@Table(name = "regions")
class RegionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "code", length = 32, nullable = false, updatable = false)
    var code: String,

    @Column(name = "name", length = 256, nullable = false)
    var name: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant
)
