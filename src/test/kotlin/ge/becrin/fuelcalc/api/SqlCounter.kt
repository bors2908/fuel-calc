package ge.becrin.fuelcalc.api

import java.util.concurrent.atomic.AtomicInteger

class SqlCounter {
    private val selects = AtomicInteger()

    @Volatile
    var afterSelect: ((String) -> Unit)? = null

    fun executed(sql: String) {
        val statement = sql.trimStart()

        if (statement.startsWith("SELECT", true) || statement.startsWith("WITH", true)) {
            selects.incrementAndGet()
            afterSelect?.invoke(sql)
        }
    }

    fun reset() {
        selects.set(0)
    }

    fun count(): Int =
        selects.get()
}
