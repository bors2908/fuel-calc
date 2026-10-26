package ge.becrin.fuelcalc.archive.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface ArchiveStateRepository : JpaRepository<ArchiveStateEntity, Short>
