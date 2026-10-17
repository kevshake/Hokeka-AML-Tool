package com.posgateway.aml.controller.analytics;

import com.posgateway.aml.service.analytics.DecisionStatsService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only dashboard aggregates over cloud-held decision data (R11): the hourly decision rollup and
 * the decision records. Never transaction data, never amounts.
 *
 * <ul>
 *   <li>{@code GET /decision-stats/summary?days=7}: daily decision counts, today vs yesterday, the
 *       window totals, score bands, risk indicators and the top merchants by flagged decisions;</li>
 *   <li>{@code GET /decision-stats/recent?limit=10}: the most recent decision records (replaces the
 *       dashboards' "recent transactions").</li>
 * </ul>
 * PSP users always get their own PSP (a {@code pspId} naming another PSP is 403); platform staff may
 * name a PSP or get every PSP's aggregate.
 */
@RestController
@RequestMapping("/decision-stats")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','APP_CONTROLLER','COMPLIANCE_OFFICER','MLRO',"
        + "'ANALYST','SENIOR_ANALYST','INVESTIGATOR','AUDITOR','PSP_ADMIN','PSP_ANALYST','PSP_USER','VIEWER')")
public class DecisionStatsController {

    private final DecisionStatsService stats;
    private final PspIsolationService isolation;

    /** Wires the endpoints. */
    public DecisionStatsController(DecisionStatsService stats, PspIsolationService isolation) {
        this.stats = stats;
        this.isolation = isolation;
    }

    /** Thrown when a PSP user names another PSP. */
    static final class ForeignPspException extends RuntimeException {
        /** No message: the response is a bare 403. */
        ForeignPspException() {
            super("pspId names another PSP");
        }
    }

    /** {@code GET /decision-stats/summary}. */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@RequestParam(defaultValue = "7") int days,
                                                       @RequestParam(required = false) Long pspId,
                                                       @RequestParam(defaultValue = "5") int topMerchants) {
        Long psp;
        try {
            psp = scope(pspId);
        } catch (ForeignPspException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        int n = DecisionStatsService.clampDays(days);
        Instant today = stats.startOfToday();
        Instant now = stats.now();
        Instant yesterday = today.minus(Duration.ofDays(1));
        List<DecisionStatsService.Day> series = stats.daily(psp, n);
        Instant windowStart = series.isEmpty() ? today : stats.startOf(series.get(0).date());
        DecisionStatsService.Counts todayCounts = stats.between(psp, today, now.plusSeconds(1));
        DecisionStatsService.Counts yesterdayCounts = stats.between(psp, yesterday, today);
        DecisionStatsService.Counts window = stats.between(psp, windowStart, now.plusSeconds(1));
        Instant last24h = now.minus(Duration.ofHours(24));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pspId", psp);
        body.put("days", n);
        body.put("generatedAt", now.toString());
        body.put("source", "DECISION_RECORDS");
        body.put("today", todayCounts.toMap());
        body.put("yesterday", yesterdayCounts.toMap());
        body.put("window", window.toMap());
        body.put("trends", Map.of(
                "recordsDelta", percentDelta(todayCounts.records(), yesterdayCounts.records()),
                "flaggedDelta", percentDelta(todayCounts.flagged(), yesterdayCounts.flagged())));
        body.put("daily", series.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>(d.counts().toMap());
            m.put("date", d.date().toString());
            m.put("label", d.label());
            return m;
        }).toList());
        body.put("scoreBands24h", stats.scoreBands(psp, last24h, now.plusSeconds(1)));
        body.put("highScore24h", stats.highScoreCount(psp, last24h, now.plusSeconds(1)));
        DecisionStatsService.Counts last24 = stats.between(psp, last24h, now.plusSeconds(1));
        body.put("last24h", last24.toMap());
        body.put("topMerchants", stats.topMerchants(psp, windowStart, now.plusSeconds(1), topMerchants));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /** {@code GET /decision-stats/recent}: newest decision records first. */
    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> recent(@RequestParam(defaultValue = "10") int limit,
                                                      @RequestParam(required = false) Long pspId) {
        Long psp;
        try {
            psp = scope(pspId);
        } catch (ForeignPspException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pspId", psp);
        body.put("items", stats.recent(psp, limit));
        body.put("source", "DECISION_RECORDS");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /**
     * The PSP to aggregate: the caller's own PSP for PSP users ({@code requested} must be absent or
     * equal), {@code requested} (or every PSP when {@code null}) for platform staff.
     */
    Long scope(Long requested) {
        if (isolation.isPlatformAdministrator()) {
            return requested != null && requested > 0 ? requested : null;
        }
        Long own = isolation.getCurrentUserPspId();
        if (requested != null && !requested.equals(own)) {
            throw new ForeignPspException();
        }
        return own;
    }

    /** Percent change from {@code before} to {@code after}, one decimal; 0 when there is no base. */
    static double percentDelta(long after, long before) {
        if (before <= 0) {
            return 0.0;
        }
        return Math.round(((double) (after - before) / before) * 1000.0) / 10.0;
    }
}
