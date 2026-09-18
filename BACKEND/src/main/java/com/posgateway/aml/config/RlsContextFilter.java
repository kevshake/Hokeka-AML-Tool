package com.posgateway.aml.config;

import jakarta.servlet.*;
import com.posgateway.aml.config.tenant.PspTenantFilter;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.security.PspIsolationService;
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

    private final PspIsolationService pspIsolationService;

    public RlsContextFilter(PspIsolationService pspIsolationService) {
        this.pspIsolationService = pspIsolationService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            if (pspIsolationService.isPlatformAdministrator(user)) {
                RlsContextHolder.setCurrentPspId(PspTenantFilter.PLATFORM_ADMIN_PSP_ID);
            } else if (user.getPsp() != null && user.getPsp().getPspId() != null) {
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
