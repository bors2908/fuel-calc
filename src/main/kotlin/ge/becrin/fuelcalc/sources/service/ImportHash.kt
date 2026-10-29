package ge.becrin.fuelcalc.sources.service

import java.security.MessageDigest

object ImportHash {
    fun hash(fields: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")

        for (field in fields) {
            val value = "${field.length}:$field"

            digest.update(value.toByteArray(Charsets.UTF_8))
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
