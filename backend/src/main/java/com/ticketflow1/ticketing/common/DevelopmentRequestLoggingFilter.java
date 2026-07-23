package com.ticketflow1.ticketing.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Development-oriented request tracing.
 *
 * The app already has domain audit logs and a global exception handler. This
 * filter is different: it records every HTTP request in a compact, grep-able
 * format so local/Cloud Run logs can answer "what endpoint failed, how long did
 * it take, and which request id should I search for?"
 */
@Component
public class DevelopmentRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentRequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Value("${app.dev-logging.enabled:true}")
    private boolean enabled;

    @Value("${app.dev-logging.slow-request-ms:1000}")
    private long slowRequestMs;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestId(request);
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            String method = request.getMethod();
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String fullPath = query == null ? path : path + "?" + query;
            int status = response.getStatus();
            String remote = request.getHeader("X-Forwarded-For");
            if (remote == null || remote.isBlank()) remote = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");

            if (status >= 500) {
                log.error("api_request_failed requestId={} method={} path={} status={} durationMs={} remote={} userAgent={}",
                        requestId, method, fullPath, status, durationMs, remote, safe(userAgent));
            } else if (status >= 400 || durationMs >= slowRequestMs) {
                log.warn("api_request_attention requestId={} method={} path={} status={} durationMs={} remote={} userAgent={}",
                        requestId, method, fullPath, status, durationMs, remote, safe(userAgent));
            } else {
                log.info("api_request requestId={} method={} path={} status={} durationMs={} remote={}",
                        requestId, method, fullPath, status, durationMs, remote);
            }
            MDC.remove("requestId");
        }
    }

    private String requestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && incoming.matches("[A-Za-z0-9._:-]{8,80}")) return incoming;
        return UUID.randomUUID().toString();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.length() > 160 ? value.substring(0, 160) : value;
    }
}
