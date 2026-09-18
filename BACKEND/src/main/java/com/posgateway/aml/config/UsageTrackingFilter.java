package com.posgateway.aml.config;

import com.posgateway.aml.dto.psp.ApiUsageEvent;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.psp.ApiUsageTrackingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Intercepts every API request under /api/v1/, maps it to a service type,
 * and fires an async usage-log write to the database so PSP consumption
 * reporting and invoice generation have real data.
 *
 * <p>Only billable endpoints are tracked. Health, actuator, Swagger, and
 * static-resource paths are excluded. The filter never blocks or delays
 * the request — {@link ApiUsageTrackingService#logRequest} is {@code @Async}.
 */
@Component
@Order(2)
public class UsageTrackingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingFilter.class);

    /** URL prefix that the filter watches. */
    private static final String BASE_PREFIX = "/api/v1";

    /**
     * A billable service: the canonical service type (which MUST match a seeded
     * {@code billing_rates.service_type}) and the HTTP methods that count as
     * billable usage. Only the work-performing verb is charged, so dashboard/list
     * GET requests are never billed.
     */
    private record ServiceMapping(String serviceType, Set<String> billableMethods) {}

    /**
     * Ordered URL→service mapping. First PATH match wins, so more specific
     * patterns are registered first (e.g. {@code /sanctions/screen/person}
     * before the generic {@code /sanctions/screen}). A {@link LinkedHashMap}
     * guarantees deterministic first-match iteration.
     *
     * <p>Every service type here corresponds to a rate seeded in {@code billing_rates}
     * (W36-6 fix: the comment previously said "V149 + V167", which was wrong on both counts —
     * V167 seeds nothing billing-related, and two of these types come from V147, not V149.
     * Accurate breakdown: {@code TRANSACTION_MONITORING}, {@code AML_SCREENING},
     * {@code SANCTIONS_SCREENING_PERSON}/{@code _ORGANIZATION}, {@code KYC_VERIFICATION},
     * {@code COMPLIANCE_CASE_CREATION} — V149; {@code RISK_ASSESSMENT}, {@code REPORT_GENERATION}
     * — V147; {@code SAR_FILING}, {@code CBK_REPORTING} — V216, previously unseeded entirely,
     * meaning every SAR filing and CBK submission billed at $0 until that migration), so
     * {@code calculateUsageCost} resolves a non-zero cost for each metered request — keeping the
     * usage→cost→invoice pipeline live end-to-end.
     */
    private static final Map<Pattern, ServiceMapping> URL_SERVICE_MAP = new LinkedHashMap<>();

    private static void map(String regex, String serviceType, String... methods) {
        URL_SERVICE_MAP.put(Pattern.compile(regex), new ServiceMapping(serviceType, Set.of(methods)));
    }

    static {
        // Sanctions screening — most specific first (person/organization before the generic screen).
        map("^/api/v1/sanctions/screen/person.*",       "SANCTIONS_SCREENING_PERSON",       "POST");
        map("^/api/v1/sanctions/screen/organization.*", "SANCTIONS_SCREENING_ORGANIZATION", "POST");
        map("^/api/v1/sanctions/screen.*",              "SANCTIONS_SCREENING_PERSON",       "POST");
        // AML checks / anomaly detection / batch screening.
        map("^/api/v1/aml/check.*",                     "AML_SCREENING", "POST");
        map("^/api/v1/aml/detection.*",                 "AML_SCREENING", "POST");
        map("^/api/v1/screening/.*",                    "AML_SCREENING", "POST");
        // Transaction monitoring — charged per ingested transaction.
        map("^/api/v1/transactions/ingest.*",           "TRANSACTION_MONITORING", "POST");
        // Risk assessment.
        map("^/api/v1/risk-assessment/assess.*",        "RISK_ASSESSMENT", "POST");
        // Report generation (the generate action only; preview/chart are non-billable reads).
        map("^/api/v1/reports/generate.*",              "REPORT_GENERATION", "POST");
        // KYC / merchant onboarding verification.
        map("^/api/v1/merchants/onboard.*",             "KYC_VERIFICATION", "POST");
        // Compliance case creation (create verb only; reads are not billed).
        map("^/api/v1/cases.*",                         "COMPLIANCE_CASE_CREATION", "POST");
        // SAR filing.
        map("^/api/v1/compliance/sar.*",                "SAR_FILING", "POST");
        // CBK regulatory submissions (create or replay).
        map("^/api/v1/compliance/cbk.*",                "CBK_REPORTING", "POST", "PUT");
    }

    /** Paths we never track (health checks, docs, static assets). */
    private static final Pattern[] EXCLUDE_PATTERNS = {
            Pattern.compile("^/api/v1/(auth/login|auth/register|health|swagger|v3/api-docs).*"),
            Pattern.compile("^/actuator/.*"),
            Pattern.compile("^/error$"),
    };

    private final ApiUsageTrackingService trackingService;

    public UsageTrackingFilter(ApiUsageTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith(BASE_PREFIX)) return true;
        for (Pattern p : EXCLUDE_PATTERNS) {
            if (p.matcher(path).matches()) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Wrap the request/response so we can read the body (consumed once)
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper resp = new ContentCachingResponseWrapper(response);

        long startNs = System.nanoTime();

        try {
            chain.doFilter(req, resp);
        } finally {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            resolveAndLog(req, resp, elapsedMs);
            // Ensure the cached response body is written back to the real output stream
            resp.copyBodyToResponse();
        }
    }

    private void resolveAndLog(ContentCachingRequestWrapper req,
                                ContentCachingResponseWrapper resp,
                                long elapsedMs) {
        try {
            String path = req.getRequestURI();
            String method = req.getMethod();
            int status = resp.getStatus();

            // Determine service type from path + method (only work-performing verbs bill)
            String serviceType = resolveServiceType(path, method);
            if (serviceType == null) return; // unmapped path or non-billable method

            // PSP ID from security context
            Long pspId = null;
            Long userId = null;
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof User user) {
                pspId = user.getPsp() != null ? user.getPsp().getPspId() : null;
                userId = user.getId();
            }
            if (pspId == null) return; // no PSP context — nothing to bill

            ApiUsageEvent event = ApiUsageEvent.builder()
                    .pspId(pspId)
                    .userId(userId)
                    .endpoint(path)
                    .httpMethod(method)
                    .responseStatus(status)
                    .responseTimeMs((int) Math.min(elapsedMs, Integer.MAX_VALUE))
                    .serviceType(serviceType)
                    .requestId(UUID.randomUUID().toString().substring(0, 12))
                    .timestamp(LocalDateTime.now())
                    .build();

            trackingService.logRequest(event);

        } catch (Exception e) {
            // Usage tracking must never fail the request.
            log.debug("Usage tracking skipped for {}: {}", req.getRequestURI(), e.getMessage());
        }
    }

    /**
     * Resolves the canonical billing service type for a request, or {@code null}
     * if the path is unmapped or the HTTP method is not a billable verb for the
     * matched service. First PATH match wins; once a path matches, a non-billable
     * method yields {@code null} (we do not fall through to a less specific pattern).
     */
    static String resolveServiceType(String path, String method) {
        for (Map.Entry<Pattern, ServiceMapping> entry : URL_SERVICE_MAP.entrySet()) {
            if (entry.getKey().matcher(path).matches()) {
                ServiceMapping mapping = entry.getValue();
                return mapping.billableMethods().contains(method) ? mapping.serviceType() : null;
            }
        }
        return null;
    }
}