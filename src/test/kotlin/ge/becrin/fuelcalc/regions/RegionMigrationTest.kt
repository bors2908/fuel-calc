package ge.becrin.fuelcalc.regions

import liquibase.integration.spring.SpringLiquibase
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals

@Testcontainers
class RegionMigrationTest {
    @Test
    fun testMigrationMarksOnlyRegionsWithExistingPrices() {
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val jdbc = JdbcTemplate(dataSource)

        jdbc.execute("CREATE TABLE regions (id bigint PRIMARY KEY)")
        jdbc.execute("CREATE TABLE fuel_prices (region_id bigint NOT NULL REFERENCES regions(id))")
        jdbc.execute("CREATE TABLE archive_state (id smallint PRIMARY KEY, revision bigint, updated_at timestamptz)")
        jdbc.update("INSERT INTO regions (id) VALUES (1), (2), (3)")
        jdbc.update("INSERT INTO fuel_prices (region_id) VALUES (1), (1), (3)")
        jdbc.update("INSERT INTO archive_state (id, revision, updated_at) VALUES (1, 5, CURRENT_TIMESTAMP)")

        val migration = SpringLiquibase()
        migration.dataSource = dataSource
        migration.changeLog = "classpath:/db/changelog/changes/000013.xml"
        migration.afterPropertiesSet()

        assertEquals(listOf(1L, 3L), jdbc.queryForList("SELECT id FROM regions WHERE has_data ORDER BY id", Long::class.java))
        assertEquals(6L, jdbc.queryForObject("SELECT revision FROM archive_state WHERE id = 1", Long::class.java))

        jdbc.update("INSERT INTO regions (id) VALUES (4)")

        assertEquals(false, jdbc.queryForObject("SELECT has_data FROM regions WHERE id = 4", Boolean::class.java))
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }
}
