package com.posgateway.aml.config.security;

import com.posgateway.aml.config.onprem.HokekaAuthProperties;
import com.posgateway.aml.service.onprem.OnPremServiceGate;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Fail-closed gate for on-prem deployments: when the Hokeka service lease is missing/expired
 * or a mandatory check-in failed, block transaction ingest / scoring / AML check APIs.
 * Health, auth, and admin diagnostics remain reachable.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 15)
public class OnPremLeaseGateFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OnPremLeaseGateFilter.class);

    private static final List<String> PROTECTED_PATTERNS = List.of(
            "/transactions/**",
            "/api/v1/transactions/**",
            "/aml/**",
            "/api/v1/aml/**",
            "/screening/**",
            "/api/v1/screening/**",
            "/batch/**",
            "/api/v1/batch/**",
            "/monitoring/**",
            "/api/v1/monitoring/**",
            "/payments/**",
            "/api/v1/payments/**",
            "/mobile-money/**",
            "/api/v1/mobile-money/**",
            "/multi-asset/**",
            "/api/v1/multi-asset/**",
            "/crypto/**",
            "/api/v1/crypto/**",
            "/virtual-assets/**",
            "/api/v1/virtual-assets/**"
    );

    private static final List<String> ALWAYS_ALLOW = List.of(
            "/actuator/**",
            "/api/v1/actuator/**",
            "/auth/**",
            "/api/v1/auth/**",
            "/onprem/auth/**",
            "/api/v1/onprem/auth/**",
            "/error"
    );

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final HokekaAuthProperties properties;
    private final OnPremServiceGate gate;

    public OnPremLeaseGateFilter(HokekaAuthProperties properties, OnPremServiceGate gate) {
        this.properties = properties;
        this.gate = gate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = pathWithinApplication(request);
        for (String pattern : ALWAYS_ALLOW) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        boolean protectedPath = false;
        for (String pattern : PROTECTED_PATTERNS) {
            if (matcher.match(pattern, path)) {
                protectedPath = true;
                break;
            }
        }
        return !protectedPath;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (gate.isOperationsAllowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        OnPremServiceGate.LeaseState state = gate.current();
        log.warn("Blocked {} {} — on-prem service authorization STOPPED ({})",
                request.getMethod(), request.getRequestURI(), state.stopReason());
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String reason = state.stopReason() != null ? state.stopReason() : "Service authorization required";
        response.getWriter().write(
                "{\"error\":\"On-prem service authorization stopped\","
                        + "\"code\":\"SERVICE_AUTHORIZATION_STOPPED\","
                        + "\"reason\":\"" + escape(reason) + "\","
                        + "\"mode\":\"" + state.mode().name() + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            String stripped = uri.substring(context.length());
            return stripped.isEmpty() ? "/" : stripped;
        }
        return uri;
    }
}
