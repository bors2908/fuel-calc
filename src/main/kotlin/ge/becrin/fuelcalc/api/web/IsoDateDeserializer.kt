package ge.becrin.fuelcalc.api.web

import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import java.time.LocalDate
import java.time.format.DateTimeParseException

class IsoDateDeserializer : ValueDeserializer<LocalDate>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext
    ): LocalDate {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(LocalDate::class.java, "Expected an ISO date string")
        }

        return try {
            LocalDate.parse(parser.string)
        } catch (error: DateTimeParseException) {
            context.reportInputMismatch(LocalDate::class.java, "Expected an ISO date string")
        }
    }
}
