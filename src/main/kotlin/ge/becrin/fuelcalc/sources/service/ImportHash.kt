package ge.becrin.fuelcalc.sources.service

import java.math.BigDecimal
import java.security.MessageDigest

object ImportHash {
    fun hash(fields: List<Any?>): String {
        val digest = MessageDigest.getInstance("SHA-256")

        for (field in fields) {
            val value =
                when (field) {
                    null -> "-1:"
                    is BigDecimal -> field.stripTrailingZeros().toPlainString().let { "${it.length}:$it" }
                    else -> field.toString().let { "${it.length}:$it" }
                }

            digest.update(value.toByteArray(Charsets.UTF_8))
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
