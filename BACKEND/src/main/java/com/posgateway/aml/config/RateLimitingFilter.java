package com.posgateway.aml.config;

import com.posgateway.aml.service.psp.QuotaService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1) // Run early
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

        // Only rate limit API paths, skip static resources or health checks if any
        if (path.startsWith("/api/v1/")) {
            String pspCode = httpRequest.getHeader("X-PSP-CODE");

            if (pspCode != null && !pspCode.isEmpty()) {
                if (!quotaService.isRequestAllowed(pspCode)) {
                    log.warn("Rate limit exceeded for PSP: {}", pspCode);
                    httpResponse.setStatus(429); // Too Many Requests
                    httpResponse.getWriter().write("Quota Exceeded");
                    return;
                }
            }
            // Note: If X-PSP-CODE is missing, we proceed.
            // Real auth logic would reject unauthenticated calls later in the chain.
        }

        chain.doFilter(request, response);
    }
}
