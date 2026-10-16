package com.posgateway.aml.config.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.security.PspApiKeyService;
import com.posgateway.aml.service.security.PspApiKeyService.AuthenticatedApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Authenticates PSP machine integrations with an issued API key ({@code hka_…}) on the machine
 * ingest endpoints, alongside (not instead of) Console session authentication.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Key in {@code X-Api-Key: hka_…} or {@code Authorization: Bearer hka_…}.</li>
 *   <li>Only on {@link #MACHINE_PATTERNS} (transaction ingest + assessment read-back). A key sent to
 *       any other path is ignored, so it can never reach Console/admin APIs.</li>
 *   <li>Verified by {@link PspApiKeyService#authenticate(String)}: hash lookup, constant-time
 *       comparison, rejected when revoked, expired, or the PSP is not ACTIVE.</li>
 *   <li>Success binds a transient {@link User} principal to the key's PSP with the single role
 *       {@code ROLE_PSP_INTEGRATION}, so every downstream tenant check
 *       ({@code PspIsolationService}, tenant filters, {@code PspActivationFilter}) scopes the call to
 *       that PSP exactly as for a PSP user. No HTTP session is created.</li>
 *   <li>A key that is presented but invalid gets {@code 401} immediately (JSON), never a silent
 *       fall-through to anonymous.</li>
 *   <li>Rate limits: per key ({@code hokeka.api-keys.rate-limit-per-minute}, default 1200) and per
 *       client IP for failed attempts ({@code hokeka.api-keys.failed-attempts-per-minute}, default
 *       20); both answer {@code 429}.</li>
 * </ul>
 * An already-authenticated request (Console session) is left untouched.
 */
@Component
public class PspApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PspApiKeyAuthenticationFilter.class);

    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final String MACHINE_ROLE = "PSP_INTEGRATION";
    /** Request attribute carrying the authenticated key id (for audit/usage). */
    public static final String API_KEY_ID_ATTRIBUTE = PspApiKeyAuthenticationFilter.class.getName() + ".keyId";
    /**
     * The request attribute {@code org.springframework.security.web.session.SessionManagementFilter}
     * sets once it has processed a request (its private FILTER_APPLIED constant); present means "skip".
     */
    static final String SESSION_MANAGEMENT_APPLIED_ATTRIBUTE = "__spring_security_session_mgmt_filter_applied";

    /** Machine endpoints, relative to the servlet context path. */
    static final List<String> MACHINE_PATTERNS = List.of(
            "/transactions/ingest",
            "/transactions/ingest/batch",
            "/transactions/*/assessment");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final PspApiKeyService apiKeyService;
    private final int perKeyPerMinute;
    private final int failedPerIpPerMinute;
    private final Cache<Long, AtomicInteger> perKeyCounts;
    private final Cache<String, AtomicInteger> failedPerIp;

    public PspApiKeyAuthenticationFilter(PspApiKeyService apiKeyService,
                                         @Value("${hokeka.api-keys.rate-limit-per-minute:1200}") int perKeyPerMinute,
                                         @Value("${hokeka.api-keys.failed-attempts-per-minute:20}") int failedPerIpPerMinute) {
        this.apiKeyService = apiKeyService;
        this.perKeyPerMinute = Math.max(1, perKeyPerMinute);
        this.failedPerIpPerMinute = Math.max(1, failedPerIpPerMinute);
        this.perKeyCounts = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();
        this.failedPerIp = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(100_000).build();
    }

    /** True when the request targets one of the machine endpoints. */
    static boolean isMachinePath(HttpServletRequest request, AntPathMatcher matcher) {
        String path = pathWithinApplication(request);
        for (String pattern : MACHINE_PATTERNS) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** Request URI without the servlet context path (e.g. {@code /api/v1}). */
    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        // Tolerate deployments where /api/v1 reaches the app without a context path.
        if (uri.startsWith("/api/v1/")) {
            uri = uri.substring("/api/v1".length());
        }
        return uri;
    }

    /**
     * Extracts a presented key from {@code X-Api-Key}, or from {@code Authorization: Bearer hka_…}.
     * A Bearer token that is not an API key (no {@code hka_} prefix) is not ours and is ignored.
     */
    static String extractKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (token.startsWith(PspApiKeyService.KEY_PREFIX)) {
                return token;
            }
        }
        return null;
    }

    /** Only machine paths that actually carry a key are processed. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isMachinePath(request, matcher) || extractKey(request) == null;
    }

    /**
     * Ingest returns a {@code CompletableFuture}, so Spring MVC finishes the response on an ASYNC
     * dispatch. There is no session to restore the identity from, so the key is re-verified on that
     * dispatch too (without counting it against the rate limit again).
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /** Verifies the key, enforces rate limits and binds the PSP-scoped machine principal. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()
                && !(existing instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        AtomicInteger failures = failedPerIp.get(ip, k -> new AtomicInteger());
        if (failures.get() >= failedPerIpPerMinute) {
            reject(response, 429, "Too many failed API key attempts; retry later.");
            return;
        }

        AuthenticatedApiKey key = apiKeyService.authenticate(extractKey(request)).orElse(null);
        if (key == null) {
            failures.incrementAndGet();
            log.warn("Rejected API key on {} from {} (invalid, revoked, expired or PSP inactive)",
                    request.getRequestURI(), ip);
            reject(response, 401, "Invalid, revoked or expired API key.");
            return;
        }

        boolean asyncDispatch = request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC;
        int used = asyncDispatch
                ? 0
                : perKeyCounts.get(key.keyId(), k -> new AtomicInteger()).incrementAndGet();
        if (used > perKeyPerMinute) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "API key rate limit exceeded (" + perKeyPerMinute + " requests/minute).");
            return;
        }

        User principal = machinePrincipal(key);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.setAttribute(API_KEY_ID_ATTRIBUTE, key.keyId());
        // Keep machine calls stateless. Spring Security's SessionManagementFilter runs after this
        // filter; seeing a fresh non-anonymous authentication it ran the session strategies
        // (concurrency registration creates an HTTP session) and SAVED the machine identity into that
        // session. The JSESSIONID then authenticated later calls with no key, a tampered key or a
        // revoked key, and every ingest call allocated a server-side session. Marking the request as
        // already handled makes SessionManagementFilter skip it entirely.
        request.setAttribute(SESSION_MANAGEMENT_APPLIED_ATTRIBUTE, Boolean.TRUE);
        try {
            chain.doFilter(request, response);
        } finally {
            // Stateless: never let the machine identity leak into a pooled thread or a session.
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Builds the transient (never persisted) principal: username {@code api-key:<prefix>}, bound to
     * the key's PSP, role {@code PSP_INTEGRATION} with no extra permissions.
     */
    static User machinePrincipal(AuthenticatedApiKey key) {
        Role role = new Role();
        role.setName(MACHINE_ROLE);
        role.setDescription("PSP machine integration (API key)");
        role.setPermissions(new HashSet<>());
        return User.builder()
                .username("api-key:" + key.keyPrefix())
                .email("api-key+" + key.keyId() + "@machine.invalid")
                .passwordHash("")
                .role(role)
                .psp(key.psp())
                .enabled(true)
                .build();
    }

    /** Writes a JSON error without echoing the presented key. */
    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"error\":\""
                + (status == 401 ? "UNAUTHORIZED" : "TOO_MANY_REQUESTS") + "\",\"message\":\""
                + message.replace("\"", "'") + "\"}");
    }
}
