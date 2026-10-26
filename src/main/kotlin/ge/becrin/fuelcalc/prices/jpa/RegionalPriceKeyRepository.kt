package ge.becrin.fuelcalc.prices.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface RegionalPriceKeyRepository : JpaRepository<RegionalPriceKeyEntity, Long>
