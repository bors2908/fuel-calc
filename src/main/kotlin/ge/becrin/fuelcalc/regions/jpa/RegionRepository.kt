package ge.becrin.fuelcalc.regions.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RegionRepository : JpaRepository<RegionEntity, Long> {
    @Query("SELECT r FROM Region r WHERE r.code IN :codes ORDER BY r.code")
    fun findSelected(@Param("codes") codes: List<String>): List<RegionEntity>

    @Query("SELECT r FROM Region r ORDER BY r.name, r.code")
    fun findOrdered(): List<RegionEntity>
}
