package com.posgateway.aml.config.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production Rate Limiting Filter
 * 
 * Implements rate limiting based on client IP address.
 * Uses Caffeine cache for efficient in-memory rate tracking.
 * 
 * Limits:
 * - General API: 100 requests per minute per IP
 * - Auth endpoints: 10 requests per minute per IP
 * - Burst capacity: 20 requests
 */
@Component
@Profile("production")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ProductionRateLimitFilter implements Filter {

    @Value("${rate.limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${rate.limit.requests-per-minute:100}")
    private int requestsPerMinute;

    @Value("${rate.limit.burst-size:20}")
    private int burstSize;

    @Value("${rate.limit.auth-endpoint-rpm:10}")
    private int authRequestsPerMinute;

    // Cache to track request counts per IP
    private LoadingCache<String, AtomicInteger> requestCountsPerIp;
    
    // Cache for auth endpoint request counts
    private LoadingCache<String, AtomicInteger> authRequestCountsPerIp;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialize caches with 1-minute expiration
        requestCountsPerIp = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build(key -> new AtomicInteger(0));
        
        authRequestCountsPerIp = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build(key -> new AtomicInteger(0));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (!rateLimitEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIp(httpRequest);
        String requestUri = httpRequest.getRequestURI();

        // Check if this is an auth endpoint
        boolean isAuthEndpoint = requestUri.contains("/auth/") || 
                                 requestUri.contains("/login") ||
                                 requestUri.contains("/password-reset");

        if (isRateLimited(clientIp, isAuthEndpoint)) {
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please try again later.\"}"
            );
            return;
        }

        // Add rate limit headers
        int remaining = getRemainingRequests(clientIp, isAuthEndpoint);
        httpResponse.setHeader("X-RateLimit-Limit", 
            String.valueOf(isAuthEndpoint ? authRequestsPerMinute : requestsPerMinute));
        httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        chain.doFilter(request, response);
    }

    /**
     * Check if the client IP has exceeded rate limits
     */
    private boolean isRateLimited(String clientIp, boolean isAuthEndpoint) {
        if (isAuthEndpoint) {
            AtomicInteger count = authRequestCountsPerIp.get(clientIp);
            return count.incrementAndGet() > authRequestsPerMinute;
        } else {
            AtomicInteger count = requestCountsPerIp.get(clientIp);
            return count.incrementAndGet() > (requestsPerMinute + burstSize);
        }
    }

    /**
     * Get remaining requests for the client IP
     */
    private int getRemainingRequests(String clientIp, boolean isAuthEndpoint) {
        if (isAuthEndpoint) {
            int used = authRequestCountsPerIp.get(clientIp).get();
            return Math.max(0, authRequestsPerMinute - used);
        } else {
            int used = requestCountsPerIp.get(clientIp).get();
            return Math.max(0, (requestsPerMinute + burstSize) - used);
        }
    }

    /**
     * Extract client IP address, handling proxies
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            // Get the first IP in the chain (original client)
            return xfHeader.split(",")[0].trim();
        }
        
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        
        return request.getRemoteAddr();
    }

    @Override
    public void destroy() {
        // Cleanup
    }
}
