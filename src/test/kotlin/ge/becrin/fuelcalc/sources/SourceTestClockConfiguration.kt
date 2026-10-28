package ge.becrin.fuelcalc.sources

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@TestConfiguration(proxyBeanMethods = false)
class SourceTestClockConfiguration {
    @Bean
    @Primary
    fun sourceTestClock(): Clock =
        clock

    companion object {
        private val clock = Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC)

        @Bean
        @JvmStatic
        fun schedulerTestClock(): BeanPostProcessor =
            object : BeanPostProcessor {
                override fun postProcessAfterInitialization(
                    bean: Any,
                    beanName: String
                ): Any {
                    if (bean is ThreadPoolTaskScheduler) {
                        bean.clock = clock
                    }

                    return bean
                }
            }
    }
}
