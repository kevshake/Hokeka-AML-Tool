package com.posgateway.aml.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Row Level Security Context Filter
 * Sets the current PSP ID in a thread-local context for use in repositories.
 */
@Component
public class RlsContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            // In real implementation, extract pspId from user details or JWT
            // For now we set a default for testing
            RlsContextHolder.setCurrentPspId(1L);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            RlsContextHolder.clear();
        }
    }
}