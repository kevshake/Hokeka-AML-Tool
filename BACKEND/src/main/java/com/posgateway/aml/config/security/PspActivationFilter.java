package com.posgateway.aml.config.security;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.service.security.PspIsolationService;
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
 * Enforces that a PSP is <b>actually authorised</b> before any of its users may use the platform.
 *
 * <h2>Why this exists</h2>
 * {@code Psp.status} defaults to {@code PENDING} and {@code /psps/register} is {@code permitAll},
 * so a PSP could self-register and — because {@code Psp.isActive()} had no caller anywhere in the
 * request path — go straight on using the API. Approval was decorative. This filter makes it real:
 * a request from a user whose PSP is not {@code ACTIVE} is refused, fail-closed.
 *
 * <h2>Scope, deliberately narrow</h2>
 * The filter only refuses when there <i>is</i> an authenticated user <i>and</i> that user is bound
 * to a PSP whose status is not {@code ACTIVE}. It never touches anonymous traffic (Spring Security
 * already decides that), and it never refuses platform-level users, who legitimately have no PSP.
 * That keeps the blast radius to exactly the hole being closed.
 *
 * <h2>Ordering</h2>
 * Registered just after {@code springSecurityFilterChain}
 * ({@link SecurityProperties#DEFAULT_FILTER_ORDER}), so it executes <i>nested inside</i> the
 * security chain and the {@code SecurityContext} is populated. Running it before security would
 * see an empty context and silently do nothing.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 10)
public class PspActivationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PspActivationFilter.class);

    private static final String ACTIVE = "ACTIVE";

    /**
     * Genuinely public surfaces, mirroring the {@code permitAll} list in {@code SecurityConfig}.
     * A user of a not-yet-approved PSP must still be able to authenticate, reset a password, read
     * pricing, and hit health — otherwise they can never be onboarded.
     */
    private static final List<String> EXEMPT_PATTERNS = List.of(
            "/auth/**",
            "/psps/register",
            "/psps/auth/login",
            "/password-reset.html",
            "/pricing/**",
            "/merchants/onboard",
            "/merchants/health",
            "/actuator/**",
            "/error",
            // Machine-to-machine edge endpoints: no user session, authenticated by mTLS +
            // enrollment code, and already gated on the owning PSP being ACTIVE in
            // EdgeEnrollmentService.
            "/edge/enroll",
            "/edge/bundle",
            "/edge/metrics",
            "/edge/decision",
            // On-prem machine-to-machine lease endpoints (client credentials in body).
            "/onprem/auth/**",
            // Swagger / OpenAPI
            "/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final PspIsolationService pspIsolationService;

    public PspActivationFilter(PspIsolationService pspIsolationService) {
        this.pspIsolationService = pspIsolationService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        for (String pattern : EXEMPT_PATTERNS) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        User user;
        try {
            user = pspIsolationService.getCurrentUser();
        } catch (RuntimeException e) {
            // Never turn a lookup failure into an open door, but also never break anonymous
            // traffic: no resolvable user means Spring Security's own rules decide.
            filterChain.doFilter(request, response);
            return;
        }

        if (user == null || pspIsolationService.isPlatformAdministrator(user)) {
            filterChain.doFilter(request, response);
            return;
        }

        Psp psp = user.getPsp();
        if (psp == null || ACTIVE.equalsIgnoreCase(psp.getStatus())) {
            // Platform-level users without a PSP are handled by PspIsolationService; an ACTIVE PSP
            // is the normal path.
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Blocked request from user {} — PSP {} is {}", user.getUsername(),
                psp.getPspId(), psp.getStatus());
        writeRefusal(response, psp.getStatus());
    }

    /**
     * Distinguishable but non-leaky: the caller is already authenticated and is only ever told the
     * state of <i>their own</i> organisation, never another PSP's.
     */
    private void writeRefusal(HttpServletResponse response, String status) throws IOException {
        String code;
        String message;
        if ("PENDING".equalsIgnoreCase(status)) {
            code = "PSP_AWAITING_APPROVAL";
            message = "Your organisation's account is awaiting approval.";
        } else if ("SUSPENDED".equalsIgnoreCase(status) || "TERMINATED".equalsIgnoreCase(status)) {
            code = "PSP_ACCESS_REVOKED";
            message = "Your organisation's access has been revoked.";
        } else {
            code = "PSP_NOT_ACTIVE";
            message = "Your organisation's account is not active.";
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\",\"code\":\"" + code + "\"}");
    }

    /** Request path with the servlet context path stripped, so patterns match `/auth/**` etc. */
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
