package com.posgateway.aml.config;

import com.posgateway.aml.service.security.PspIsolationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to inject the current user's PSP ID into the MDC (Mapped Diagnostic Context)
 * for logging purposes.
 * <p>
 * This allows clustering logs by PSP ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 101) // Run after Spring Security filter chain
public class PspLoggingFilter extends OncePerRequestFilter {

    private final PspIsolationService pspIsolationService;

    public static final String MDC_KEY_PSP_ID = "pspId";
    public static final String SUPER_ADMIN_PSP_NAME = "super_admin";
    public static final String UNKNOWN_PSP = "unknown";

    public PspLoggingFilter(PspIsolationService pspIsolationService) {
        this.pspIsolationService = pspIsolationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Long pspId = null;
            String pspCode = null;

            // Attempt to get current user info safely
            // Note: This relies on SecurityContext being populated, so this filter usually needs
            // to run after Spring Security's SecurityContextHolderAwareRequestFilter
            try {
                if (pspIsolationService.getCurrentUser() != null) {
                    if (pspIsolationService.isPlatformAdministrator()) {
                        MDC.put(MDC_KEY_PSP_ID, SUPER_ADMIN_PSP_NAME);
                    } else {
                        pspCode = pspIsolationService.getCurrentUserPspCode();
                        if (pspCode != null) {
                            MDC.put(MDC_KEY_PSP_ID, pspCode);
                        } else {
                            // Fallback if PSP user has no code?
                             MDC.put(MDC_KEY_PSP_ID, UNKNOWN_PSP);
                        }
                    }
                } else {
                     MDC.put(MDC_KEY_PSP_ID, UNKNOWN_PSP);
                }
            } catch (Exception e) {
                // Don't fail request if we can't get user info
                MDC.put(MDC_KEY_PSP_ID, UNKNOWN_PSP);
            }

            filterChain.doFilter(request, response);
        } finally {
            // Clean up MDC to prevent data leaking to other requests on the same thread
            MDC.remove(MDC_KEY_PSP_ID);
        }
    }
}
