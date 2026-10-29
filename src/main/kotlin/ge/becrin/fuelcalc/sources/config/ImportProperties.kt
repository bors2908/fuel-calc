package ge.becrin.fuelcalc.sources.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalDate

@ConfigurationProperties("imports")
class ImportProperties {
    class Rosstat {
        var enabled: Boolean = true

        var fetchOnStartup: Boolean = true

        var cron: String = "0 0 */6 * * *"

        var ignoreTlsErrors: Boolean = true

        var requestTimeout: Duration = Duration.ofMinutes(2)

        var monthlyInterval: Duration = Duration.ofHours(24)

        var weeklyCatalog: String = "https://rosstat.gov.ru/compendium/document/50798"

        var monthlyCatalog: String = "https://rosstat.gov.ru/statistics/price"
    }

    class Rustata {
        var enabled: Boolean = true

        var fetchOnStartup: Boolean = true

        var cron: String = "0 30 */6 * * *"

        var baseUrl: String = "https://rustata.ru"

        var monthlyInterval: Duration = Duration.ofHours(24)

        var token: String = ""

        var requestInterval: Duration = Duration.ofSeconds(6)

        var pageSize: Int = 300

        var maxPages: Int = 10
    }

    class Benzup {
        var enabled: Boolean = true

        var fetchOnStartup: Boolean = true

        var cron: String = "0 15 0,6,12,18 * * *"

        var endpoint: String = "https://api.omt-consult.ru/index/regional"

        var token: String = ""
    }

    var enabled: Boolean = true

    var fetchOnStartup: Boolean = true

    var timeZone: String = "Europe/Moscow"

    var historyFrom: LocalDate = LocalDate.of(2026, 1, 1)

    var historyWindowsPerRun: Int = 2

    var reconciliationInterval: Duration = Duration.ofDays(7)

    var batchSize: Int = 500

    var connectTimeout: Duration = Duration.ofSeconds(10)

    var requestTimeout: Duration = Duration.ofSeconds(30)

    var maxResponseBytes: Int = 32 * 1024 * 1024

    var maxRunDuration: Duration = Duration.ofMinutes(10)

    var rosstat: Rosstat = Rosstat()

    var rustata: Rustata = Rustata()

    var benzup: Benzup = Benzup()
}
