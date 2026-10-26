package ge.becrin.fuelcalc.sources.service

import java.time.Instant

class SourceImportException(
    val code: String,

    message: String,

    val retryAt: Instant? = null,

    cause: Throwable? = null
) : RuntimeException(message, cause)
