package ge.becrin.fuelcalc.sources.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface DataSourceRepository : JpaRepository<DataSourceEntity, String>
