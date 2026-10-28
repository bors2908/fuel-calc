package ge.becrin.fuelcalc.api.web

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.type.LogicalType

@Configuration
class ApiConfiguration(private val queryBinder: ApiQueryBinder) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(queryBinder).addPathPatterns("/api/v1/**")
    }

    @Bean
    fun apiJsonCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            builder.enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            builder.withCoercionConfig(LogicalType.Textual) { config ->
                for (shape in listOf(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean)) {
                    config.setCoercion(shape, CoercionAction.Fail)
                }
            }
        }
}
