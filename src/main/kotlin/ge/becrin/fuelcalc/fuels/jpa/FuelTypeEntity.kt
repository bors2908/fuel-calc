package ge.becrin.fuelcalc.fuels.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity(name = "FuelType")
@Table(name = "fuel_types")
class FuelTypeEntity(
    @Id
    @Column(name = "code", length = 16, nullable = false, updatable = false)
    var code: String,

    @Column(name = "name", length = 64, nullable = false)
    var name: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int
)
