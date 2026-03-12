package com.posgateway.aml.config.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Security Headers Filter for Production Environment
 * 
 * Adds essential security headers to all HTTP responses:
 * - X-Content-Type-Options: prevents MIME type sniffing
 * - X-Frame-Options: prevents clickjacking
 * - X-XSS-Protection: enables XSS filtering
 * - Strict-Transport-Security: enforces HTTPS
 * - Content-Security-Policy: restricts resource loading
 * - Referrer-Policy: controls referrer information
 */
@Component
@Profile("production")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    private static final String CONTENT_SECURITY_POLICY = 
        "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https:; " +
        "font-src 'self'; " +
        "connect-src 'self' https://api.hokeka.com https://hokeka.com; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self'";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Prevent MIME type sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        
        // Prevent clickjacking
        httpResponse.setHeader("X-Frame-Options", "DENY");
        
        // XSS Protection
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        
        // HTTPS Strict Transport Security (HSTS)
        // Only add if request is secure (HTTPS)
        if (httpRequest.isSecure() || 
            "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto"))) {
            httpResponse.setHeader("Strict-Transport-Security", 
                "max-age=31536000; includeSubDomains; preload");
        }
        
        // Content Security Policy
        httpResponse.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        
        // Referrer Policy
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions Policy (formerly Feature Policy)
        httpResponse.setHeader("Permissions-Policy", 
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), " +
            "magnetometer=(), microphone=(), payment=(), usb=()");
        
        // Remove server identification
        httpResponse.setHeader("Server", "");
        
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
