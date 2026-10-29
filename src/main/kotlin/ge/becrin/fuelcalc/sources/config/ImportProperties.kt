package ge.becrin.fuelcalc.sources.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalDate

@ConfigurationProperties("imports")
class ImportProperties {
    class Regions {
        var okatoEndpoint: String = "https://showdata.rosstat.gov.ru/backbone/set/13032"

        var selectionEndpoint: String = "https://showdata.rosstat.gov.ru/backbone/set/304297"

        var cron: String = "0 0 3 * * MON"
    }

    class Rosstat {
        var baseUrl: String = "https://showdata.rosstat.gov.ru"

        var indicatorId: Long = 274422

        var goodsSetId: Long = 108929

        var startDate: LocalDate = LocalDate.of(2026, 1, 1)

        var cron: String = "0 0 0 * * *"

        var ignoreTlsErrors: Boolean = true

        var requestTimeout: Duration = Duration.ofMinutes(2)
    }

    class Rustata {
        var baseUrl: String = "https://rustata.ru"

        var dataset: String = "fedstat/indicator_37426"

        var startDate: LocalDate = LocalDate.of(2026, 1, 1)

        var token: String = ""

        var requestInterval: Duration = Duration.ofSeconds(6)

        var pageSize: Int = 300

        var maxPages: Int = 20
    }

    class Benzup {
        var endpoint: String = "https://api.omt-consult.ru/index/regional"

        var token: String = ""

        var cron: String = "0 0 12 * * *"
    }

    var enabled: Boolean = true

    var fetchOnStartup: Boolean = true

    var timeZone: String = "Europe/Moscow"

    var connectTimeout: Duration = Duration.ofSeconds(10)

    var requestTimeout: Duration = Duration.ofSeconds(30)

    var maxResponseBytes: Int = 32 * 1024 * 1024

    var maxRunDuration: Duration = Duration.ofMinutes(30)

    var regions: Regions = Regions()

    var rosstat: Rosstat = Rosstat()

    var rustata: Rustata = Rustata()

    var benzup: Benzup = Benzup()
}
