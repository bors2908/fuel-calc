package ge.becrin.fuelcalc.sources.benzup

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class BenzupRegionMapping {
    val okatoByRegionId: Map<String, String> = ClassPathResource("sources/benzup/benz-up-region-mapping.json")
        .inputStream.use { input ->
            JsonMapper.builder().build().readTree(input).properties().associate { it.key to it.value.asString() }
        }
}
