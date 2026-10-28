package ge.becrin.fuelcalc.api

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

@TestConfiguration(proxyBeanMethods = false)
class ApiTestConfiguration {
    @Bean
    @Primary
    fun apiClock(): Clock =
        Clock.fixed(Instant.parse("2026-10-26T09:10:00Z"), ZoneOffset.UTC)

    companion object {
        private val counter = SqlCounter()

        @Bean
        @JvmStatic
        fun sqlCounter(): SqlCounter =
            counter

        @Bean
        @JvmStatic
        fun sqlCounterPostProcessor(): BeanPostProcessor =
            object : BeanPostProcessor {
                override fun postProcessAfterInitialization(
                    bean: Any,
                    beanName: String
                ): Any =
                    if (bean is DataSource) CountingDataSource(bean, counter) else bean
            }
    }
}
