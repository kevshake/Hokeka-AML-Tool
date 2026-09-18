package com.hokeka.aml.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects unauthenticated calls to {@code /internal/**} unless they carry a matching
 * {@code X-Internal-Auth} header. Health and actuator endpoints are always open.
 *
 * <p>If {@code aml.internal-auth-key} is empty, the filter is disabled with a WARN log
 * so local dev / docker-compose works without secrets configured.
 */
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthFilter.class);
    private static final String HEADER = "X-Internal-Auth";

    private final String expectedKey;

    public InternalAuthFilter(@Value("${aml.internal-auth-key:}") String expectedKey) {
        this.expectedKey = expectedKey == null ? "" : expectedKey.trim();
        if (this.expectedKey.isEmpty()) {
            log.warn("aml.internal-auth-key is NOT configured — InternalAuthFilter is DISABLED. "
                    + "Set AML_MS_INTERNAL_KEY before exposing this service.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Always-open paths
        if (path.startsWith("/actuator/")
                || path.equals("/internal/v1/aml/health")
                || path.equals("/internal/v1/health")) {
            chain.doFilter(request, response);
            return;
        }

        // Filter disabled when no key is set
        if (expectedKey.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/internal/")) {
            String provided = request.getHeader(HEADER);
            if (provided == null || !constantTimeEquals(expectedKey, provided)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"missing or invalid X-Internal-Auth\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** Constant-time comparison to avoid a timing side-channel on the internal auth key. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
