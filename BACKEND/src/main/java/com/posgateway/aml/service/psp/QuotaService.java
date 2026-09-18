package com.posgateway.aml.service.psp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces two kinds of limit for a PSP tenant, keyed on the resolved numeric {@code pspId}
 * (never a client-supplied header):
 * <ul>
 *   <li><b>Rate limit</b> — a fixed per-minute window, uniform abuse protection.</li>
 *   <li><b>Monthly quota</b> — the plan's {@code maxChecksPerMonth} entitlement (data-driven via
 *       {@link EntitlementService}); no plan cap ⇒ unlimited.</li>
 * </ul>
 *
 * <p>Both counters are per-JVM (like the prior implementation); behind multiple replicas the
 * effective limit is multiplied by the replica count. The monthly usage figure is cached for 60 s,
 * so enforcement reconciles with the database each minute rather than on every request.
 */
@Service
public class QuotaService {

    private final EntitlementService entitlementService;
    private final ApiUsageLogRepository apiUsageLogRepository;

    @Value("${saas.quota.requests-per-minute:120}")
    private int requestsPerMinute;

    /** Per-PSP per-minute request counter. */
    private final Map<Long, RequestCounter> rateLimiters = new ConcurrentHashMap<>();

    /** Cached count of this month's billable requests per PSP (60 s freshness). */
    private final Cache<Long, Long> monthlyUsage = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(50_000)
            .build();

    public QuotaService(EntitlementService entitlementService, ApiUsageLogRepository apiUsageLogRepository) {
        this.entitlementService = entitlementService;
        this.apiUsageLogRepository = apiUsageLogRepository;
    }

    /** True if the PSP is within its per-minute rate window. Null pspId (non-tenant) is never limited. */
    public boolean isWithinRateLimit(Long pspId) {
        if (pspId == null) {
            return true;
        }
        return rateLimiters.computeIfAbsent(pspId, k -> new RequestCounter()).tryAcquire(requestsPerMinute);
    }

    /**
     * True if the PSP is under its plan's monthly check quota. No plan / unlimited plan ⇒ always true.
     * Enforced with up to 60 s reconciliation lag against the usage log.
     */
    public boolean isWithinMonthlyQuota(Long pspId) {
        if (pspId == null) {
            return true;
        }
        Integer limit = entitlementService.monthlyCheckLimit(pspId);
        if (limit == null || limit <= 0) {
            return true; // unlimited / no plan cap
        }
        long used = monthlyUsage.get(pspId, this::countThisMonth);
        return used < limit;
    }

    /** This month's billable request count for the PSP (cached 60 s). For portal/usage displays. */
    public long currentMonthlyUsage(Long pspId) {
        if (pspId == null) {
            return 0L;
        }
        return monthlyUsage.get(pspId, this::countThisMonth);
    }

    private long countThisMonth(Long pspId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay(); // through end of today
        return apiUsageLogRepository.countBillableRequests(pspId, start, end);
    }
}
