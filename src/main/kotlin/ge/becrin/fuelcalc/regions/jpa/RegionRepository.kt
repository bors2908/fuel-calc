package ge.becrin.fuelcalc.regions.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface RegionRepository : JpaRepository<RegionEntity, Long>
