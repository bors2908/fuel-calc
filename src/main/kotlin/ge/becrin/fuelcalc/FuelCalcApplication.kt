package ge.becrin.fuelcalc

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FuelCalcApplication

fun main(args: Array<String>) {
    runApplication<FuelCalcApplication>(*args)
}
