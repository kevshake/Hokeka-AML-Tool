package com.posgateway.aml.config;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.psp.QuotaService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Per-tenant quota enforcement. Runs after Spring Security has populated the {@code SecurityContext}
 * (same ordering the {@link UsageTrackingFilter} relies on), and resolves the tenant from the
 * <b>authenticated principal</b> — never a client-supplied header, which is trivially forgeable and
 * was the reason the previous implementation enforced nothing (nothing ever sent {@code X-PSP-CODE}).
 *
 * <p>For a PSP tenant it enforces:
 * <ol>
 *   <li>a per-minute rate limit on every API call (429 on breach), and</li>
 *   <li>the plan's monthly check quota on billable (metered) requests only.</li>
 * </ol>
 * Admin / platform users and unauthenticated calls carry no {@code pspId} and are not tenant-limited.
 */
@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitingFilter.class);

    private final QuotaService quotaService;

    public RateLimitingFilter(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }

        Long pspId = resolveTenantPspId();
        if (pspId == null) {
            // Not a PSP tenant request (admin/platform/anonymous) — no per-tenant quota applies.
            chain.doFilter(request, response);
            return;
        }

        // 1) Per-minute rate limit on every tenant API call.
        if (!quotaService.isWithinRateLimit(pspId)) {
            log.warn("Rate limit exceeded for PSP {}", pspId);
            writeTooManyRequests(httpResponse, "Rate limit exceeded. Please slow down and retry shortly.");
            return;
        }

        // 2) Monthly plan quota — only for billable (metered) requests.
        String serviceType = UsageTrackingFilter.resolveServiceType(path, httpRequest.getMethod());
        if (serviceType != null && !quotaService.isWithinMonthlyQuota(pspId)) {
            log.warn("Monthly plan quota exceeded for PSP {} on {}", pspId, serviceType);
            writeTooManyRequests(httpResponse,
                    "Monthly plan quota exceeded. Upgrade your plan or wait for the next billing period.");
            return;
        }

        chain.doFilter(request, response);
    }

    /** The numeric pspId of the authenticated tenant user, or null for non-tenant principals. */
    private Long resolveTenantPspId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            return user.getPsp() != null ? user.getPsp().getPspId() : null;
        }
        return null;
    }

    private void writeTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
