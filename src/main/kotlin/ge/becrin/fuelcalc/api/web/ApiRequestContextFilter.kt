package ge.becrin.fuelcalc.api.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class ApiRequestContextFilter(private val context: ApiRequestContext) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/v1/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val info = context.begin()
        response.setHeader("Cache-Control", "no-store")
        MDC.put("requestId", info.requestId)

        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
            context.clear()
        }
    }
}
