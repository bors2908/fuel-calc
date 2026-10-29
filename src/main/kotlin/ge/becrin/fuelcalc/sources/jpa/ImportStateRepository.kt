package ge.becrin.fuelcalc.sources.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface ImportStateRepository : JpaRepository<ImportStateEntity, String>
