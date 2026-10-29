package ge.becrin.fuelcalc.api

import java.util.concurrent.atomic.AtomicInteger

class SqlCounter {
    private val selects = AtomicInteger()

    fun executed(sql: String) {
        val statement = sql.trimStart()

        if (statement.startsWith("SELECT", true) || statement.startsWith("WITH", true)) {
            selects.incrementAndGet()
        }
    }

    fun reset() {
        selects.set(0)
    }

    fun count(): Int =
        selects.get()
}
