package ge.becrin.fuelcalc.refueling.service

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class RefuelingCalculator {
    fun totals(
        volume: BigDecimal,
        prices: Map<String, BigDecimal>
    ): Map<String, BigDecimal> =
        prices.mapValues { (_, price) -> volume.multiply(price).setScale(2, RoundingMode.HALF_UP) }
}
