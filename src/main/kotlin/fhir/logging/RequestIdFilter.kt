package fhir.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

import org.slf4j.MDC //Is the state of the art logging procedures - SLF4J + Logback/Log4j with JSON encoder.

@Component
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        chain: FilterChain
    ) {
        val requestId = req.getHeader("X-Request-ID") ?: UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
        resp.setHeader("X-Request-ID", requestId)
        try { chain.doFilter(req, resp) } finally { org.slf4j.MDC.clear() }

    }
}
