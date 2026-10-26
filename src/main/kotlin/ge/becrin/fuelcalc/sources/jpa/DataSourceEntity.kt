package ge.becrin.fuelcalc.sources.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity(name = "DataSource")
@Table(name = "data_sources")
class DataSourceEntity(
    @Id
    @Column(name = "code", length = 32, nullable = false, updatable = false)
    var code: String,

    @Column(name = "name", length = 256, nullable = false)
    var name: String,

    @Column(name = "url", columnDefinition = "text")
    var url: String? = null
)
