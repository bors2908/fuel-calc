package ge.becrin.fuelcalc.config

import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CalendarProperties::class)
class CalendarConfiguration {
    @Bean
    fun applicationClock(): Clock =
        Clock.systemUTC()
}
