package com.posgateway.aml.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Restricts the M-Pesa (Safaricom Daraja) payment callback endpoint to a configured IP/CIDR
 * allowlist (W37-3). Before this filter existed, the callback path was {@code permitAll()} in
 * {@code SecurityConfig} guarded only by the generic per-IP rate limiter — any internet host could
 * POST a forged payment-status callback and race the rate limit.
 *
 * <p>The allowlist is deliberately NOT hardcoded with Safaricom's published production IP ranges:
 * those ranges are operator-facing infrastructure data (subject to change on Safaricom's side, and
 * different between sandbox and production), not something to bake into source and get stale
 * silently. Configure real values via {@code MPESA_CALLBACK_ALLOWED_IPS} (comma-separated IPs or
 * CIDR blocks). Left unconfigured, this filter is a no-op (matching the endpoint's previous
 * behaviour exactly) and {@code EnvVarStartupValidator} WARNs about it on boot — same pattern
 * already used for {@code ADVERSE_MEDIA_ENABLED}/{@code SANCTIONS_DOWNLOAD_ENABLED}.
 */
@Component
public class MpesaCallbackIpAllowlistFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(MpesaCallbackIpAllowlistFilter.class);

    private static final List<String> CALLBACK_PATHS = List.of(
            "/api/v1/billing/payments/mpesa/callback",
            "/billing/payments/mpesa/callback"
    );

    private final List<IpAddressMatcher> matchers;
    private final boolean enabled;

    public MpesaCallbackIpAllowlistFilter(
            @Value("${mpesa.callback.allowed-ips:}") String allowedIpsCsv) {
        List<IpAddressMatcher> parsed = new ArrayList<>();
        if (allowedIpsCsv != null && !allowedIpsCsv.isBlank()) {
            for (String entry : allowedIpsCsv.split(",")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                // IpAddressMatcher accepts a bare IP or CIDR ("196.201.214.0/24"); a bare IP is
                // treated as an exact match.
                parsed.add(new IpAddressMatcher(trimmed));
            }
        }
        this.matchers = parsed;
        this.enabled = !parsed.isEmpty();
        if (!enabled) {
            logger.warn("MPESA_CALLBACK_ALLOWED_IPS is not configured — the M-Pesa callback "
                    + "endpoint accepts requests from any IP (matches pre-existing behaviour). "
                    + "Set it to Safaricom Daraja's published callback IP ranges before relying on "
                    + "this endpoint in production.");
        } else {
            logger.info("M-Pesa callback IP allowlist active with {} entr{}",
                    matchers.size(), matchers.size() == 1 ? "y" : "ies");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return CALLBACK_PATHS.stream().noneMatch(path::endsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, java.io.IOException {
        if (!enabled) {
            // No allowlist configured: preserve pre-existing (unrestricted) behaviour rather than
            // fail-closed on a filter that has nothing to check against.
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        boolean allowed = matchers.stream().anyMatch(m -> {
            try {
                return m.matches(clientIp);
            } catch (IllegalArgumentException e) {
                // Malformed remote address (shouldn't happen from a real servlet container, but
                // fail closed rather than let a matcher exception bypass the check).
                return false;
            }
        });

        if (!allowed) {
            logger.warn("Rejected M-Pesa callback from non-allowlisted IP: {}", clientIp);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Forbidden\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Same proxy-aware IP resolution as ProductionRateLimitFilter, for consistency. */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
