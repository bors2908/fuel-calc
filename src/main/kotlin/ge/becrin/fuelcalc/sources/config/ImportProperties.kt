package ge.becrin.fuelcalc.sources.config

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import java.time.Duration
import java.time.LocalDate
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
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

        @get:AssertTrue(message = "Rosstat request timeout must be positive")
        val validTimeout: Boolean
            get() =
                requestTimeout > Duration.ZERO
    }

    class Rustata {
        var baseUrl: String = "https://rustata.ru"

        var dataset: String = "fedstat/indicator_37426"

        var startDate: LocalDate = LocalDate.of(2026, 1, 1)

        var token: String = ""

        var requestInterval: Duration = Duration.ofSeconds(6)

        @field:Min(1)
        var pageSize: Int = 300

        @field:Min(1)
        var maxPages: Int = 20

        @get:AssertTrue(message = "Rustata request interval must not be negative")
        val validInterval: Boolean
            get() =
                requestInterval >= Duration.ZERO
    }

    class Benzup {
        var endpoint: String = "https://api.omt-consult.ru/index/regional"

        var token: String = ""

        var cron: String = "0 0 12 * * *"
    }

    var enabled: Boolean = true

    var fetchOnStartup: Boolean = true

    var connectTimeout: Duration = Duration.ofSeconds(10)

    var requestTimeout: Duration = Duration.ofSeconds(30)

    @field:Min(1)
    var maxResponseBytes: Int = 32 * 1024 * 1024

    var maxRunDuration: Duration = Duration.ofMinutes(30)

    @get:AssertTrue(message = "Source timeouts and run duration must be positive")
    val validDurations: Boolean
        get() =
            connectTimeout > Duration.ZERO && requestTimeout > Duration.ZERO && maxRunDuration > Duration.ZERO

    @field:Valid
    var regions: Regions = Regions()

    @field:Valid
    var rosstat: Rosstat = Rosstat()

    @field:Valid
    var rustata: Rustata = Rustata()

    @field:Valid
    var benzup: Benzup = Benzup()
}
