package ge.becrin.fuelcalc.api

import org.yaml.snakeyaml.Yaml
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.OffsetDateTime

class ApiContractAssertions {
    private val schemas: Map<String, Map<String, Any?>> = Files.newBufferedReader(Path.of("openapi.yml")).use { reader ->
        val specification = Yaml().load<Map<String, Any?>>(reader)
        @Suppress("UNCHECKED_CAST")
        val components = specification.getValue("components") as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        (components.getValue("schemas") as Map<String, Map<String, Any?>>)
    }

    fun response(
        path: String,
        status: Int,
        value: JsonNode
    ) {
        val schema =
            when {
                status >= 400 -> "ErrorResponse"
                path == "/api/v1/regions" -> "RegionList"
                path == "/api/v1/fuel-types" -> "FuelTypeList"
                path == "/api/v1/fuel-prices" -> "PricePage"
                path.contains("/observations/") -> "PriceObservationResponse"
                path.endsWith("/calculate") -> "RefuelingCalculation"
                else -> error("Unknown API path: $path")
            }
        val mismatch = mismatch(value, schemas.getValue(schema), schema)

        check(mismatch == null) { mismatch.orEmpty() + "\n" + value.toString() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mismatch(
        value: JsonNode,
        schema: Map<String, Any?>,
        path: String
    ): String? {
        val reference = schema["\$ref"] as? String

        if (reference != null) {
            return mismatch(value, schemas.getValue(reference.substringAfterLast('/')), path)
        }

        if (value.isNull) {
            return if (schema["nullable"] == true) null else "$path: unexpected null"
        }

        val enumeration = schema["enum"] as? List<*>

        if (enumeration != null && (!value.isValueNode || value.asString() !in enumeration)) {
            return "$path: unexpected enum $value"
        }

        for (part in schema["allOf"] as? List<Map<String, Any?>> ?: emptyList()) {
            mismatch(value, part, path)?.let { return it }
        }

        val variants = schema["oneOf"] as? List<Map<String, Any?>>

        if (variants != null && variants.count { mismatch(value, it, path) == null } != 1) {
            return "$path: expected exactly one matching variant"
        }

        val negative = schema["not"] as? Map<String, Any?>

        if (negative != null && mismatch(value, negative, path) == null) {
            return "$path: forbidden shape"
        }

        val required = schema["required"] as? List<String> ?: emptyList()

        if (required.any { !value.has(it) }) {
            return "$path: missing required fields ${required.filter { !value.has(it) }}"
        }

        when (schema["type"]) {
            "object" -> if (!value.isObject) return "$path: expected object"
            "array" -> {
                if (!value.isArray) return "$path: expected array"
                if (schema["minItems"] is Number && value.size() < (schema["minItems"] as Number).toInt()) {
                    return "$path: too few items"
                }
                if (schema["maxItems"] is Number && value.size() > (schema["maxItems"] as Number).toInt()) {
                    return "$path: too many items"
                }
                if (schema["uniqueItems"] == true && value.toList().distinct().size != value.size()) {
                    return "$path: duplicate items"
                }

                val items = schema["items"] as? Map<String, Any?>

                if (items != null) {
                    for ((index, item) in value.withIndex()) {
                        mismatch(item, items, "$path[$index]")?.let { return it }
                    }
                }
            }
            "string" -> {
                if (!value.isString) return "$path: expected JSON string"
                val text = value.asString()
                if (schema["minLength"] is Number && text.length < (schema["minLength"] as Number).toInt()) {
                    return "$path: string too short"
                }
                if (schema["maxLength"] is Number && text.length > (schema["maxLength"] as Number).toInt()) {
                    return "$path: string too long"
                }
                if (schema["pattern"] is String && !Regex(schema["pattern"] as String).matches(text)) {
                    return "$path: pattern mismatch"
                }
                try {
                    when (schema["format"]) {
                        "date" -> LocalDate.parse(text)
                        "date-time" -> OffsetDateTime.parse(text)
                        "uri" -> check(URI.create(text).isAbsolute)
                    }
                } catch (error: Exception) {
                    return "$path: invalid ${schema["format"]}"
                }
            }
            "integer" -> {
                if (!value.isIntegralNumber) return "$path: expected integer"
                if (schema["minimum"] is Number && value.decimalValue() < BigDecimal(schema["minimum"].toString())) {
                    return "$path: below minimum"
                }
                if (schema["maximum"] is Number && value.decimalValue() > BigDecimal(schema["maximum"].toString())) {
                    return "$path: above maximum"
                }
            }
            "boolean" -> if (!value.isBoolean) return "$path: expected boolean"
        }

        val properties = schema["properties"] as? Map<String, Map<String, Any?>> ?: emptyMap()

        for ((field, definition) in properties) {
            if (value.has(field)) {
                mismatch(value.path(field), definition, "$path.$field")?.let { return it }
            }
        }

        if (value.isObject) {
            val additional = schema["additionalProperties"]

            for ((field, child) in value.properties()) {
                if (field !in properties) {
                    if (additional == false) {
                        return "$path: unexpected field $field"
                    }

                    if (additional is Map<*, *>) {
                        mismatch(child, additional as Map<String, Any?>, "$path.$field")?.let { return it }
                    }
                }
            }
        }

        return null
    }
}
