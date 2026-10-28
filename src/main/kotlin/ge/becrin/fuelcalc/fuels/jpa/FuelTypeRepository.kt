package ge.becrin.fuelcalc.fuels.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FuelTypeRepository : JpaRepository<FuelTypeEntity, String> {
    @Query("SELECT f FROM FuelType f ORDER BY f.sortOrder, f.code")
    fun findOrdered(): List<FuelTypeEntity>
}
