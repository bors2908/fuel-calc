package ge.becrin.fuelcalc.sources.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface SourceFetchStateRepository : JpaRepository<SourceFetchStateEntity, Long>
