package ge.becrin.fuelcalc.sources

import ge.becrin.fuelcalc.sources.rosstat.RosstatRegionParser
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class RosstatRegionParserTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun testOnlyFederalCityNamesLoseDescriptionsRegardlessOfCase() {
        val full = checkNotNull(javaClass.getResourceAsStream("/sources/rosstat/federal-cities.json"))
            .use { json.readTree(it) }
        val selection = json.createArrayNode()

        full.forEach { selection.addObject().put("-298", it.path("id").asLong()) }

        val regions = RosstatRegionParser().parse(full, selection).associate { it.okatoCode to it.name }

        assertEquals(
            mapOf(
                "45000000000" to "Москва",
                "40000000000" to "САНКТ-ПЕТЕРБУРГ",
                "14000000000" to "город Белгород",
                "TEST_CAPITAL" to "СТОЛИЦА РОССИЙСКОЙ ФЕДЕРАЦИИ Москва",
                "TEST_CITY" to "Новгород",
                "TEST_SPACES" to "  Нижний   Новгород  ",
                "TEST_MIXED_CAPITAL" to "Москва"
            ),
            regions
        )
    }
}
