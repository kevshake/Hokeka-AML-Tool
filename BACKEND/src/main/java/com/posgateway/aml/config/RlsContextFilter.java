package com.posgateway.aml.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import com.posgateway.aml.entity.User;
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

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            if (user.getPsp() != null) {
                RlsContextHolder.setCurrentPspId(user.getPsp().getPspId());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            RlsContextHolder.clear();
        }
    }
}
