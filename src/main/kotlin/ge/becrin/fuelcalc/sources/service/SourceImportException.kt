package ge.becrin.fuelcalc.sources.service


class SourceImportException(
    val code: String,

    message: String,

    cause: Throwable? = null
) : RuntimeException(message, cause)
