package ge.becrin.fuelcalc.fuels.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface FuelTypeRepository : JpaRepository<FuelTypeEntity, String>
