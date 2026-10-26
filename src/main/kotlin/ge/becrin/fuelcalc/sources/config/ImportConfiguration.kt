package ge.becrin.fuelcalc.sources.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock

@Configuration
@EnableScheduling
@EnableConfigurationProperties(ImportProperties::class)
class ImportConfiguration {
    @Bean
    fun importClock(): Clock =
        Clock.systemUTC()

    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 3
        scheduler.setThreadNamePrefix("price-import-")
        scheduler.setWaitForTasksToCompleteOnShutdown(false)
        scheduler.setRemoveOnCancelPolicy(true)

        return scheduler
    }
}
