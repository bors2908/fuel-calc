package ge.becrin.fuelcalc.api.web

import ge.becrin.fuelcalc.api.error.ApiException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class ApiQueryBinder : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (request.requestURI.startsWith("/api/v1/")) {
            for ((name, values) in request.parameterMap) {
                if (values.size != 1) {
                    throw ApiException.invalid(name, "INVALID_FORMAT", "Параметр должен встречаться один раз")
                }
            }
        }

        return true
    }
}
