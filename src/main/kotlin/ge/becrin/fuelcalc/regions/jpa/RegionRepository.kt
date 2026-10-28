package ge.becrin.fuelcalc.regions.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RegionRepository : JpaRepository<RegionEntity, Long> {
    @Query("SELECT r FROM Region r WHERE r.code IN :codes ORDER BY r.code")
    fun findSelected(@Param("codes") codes: List<String>): List<RegionEntity>

    @Query(
        nativeQuery = true,
        value = "SELECT * FROM regions WHERE name ILIKE :pattern ESCAPE '!' ORDER BY name, code"
    )
    fun findMatching(@Param("pattern") pattern: String): List<RegionEntity>
}
