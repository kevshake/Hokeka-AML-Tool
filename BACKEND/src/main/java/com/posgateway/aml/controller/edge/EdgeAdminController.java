package com.posgateway.aml.controller.edge;

import com.posgateway.aml.dto.edge.EdgeNodeCreatedResponse;
import com.posgateway.aml.dto.edge.EdgeNodeRequestBody;
import com.posgateway.aml.dto.edge.EdgeNodeView;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.repository.edge.EdgeMetricsRecordRepository;
import com.posgateway.aml.service.edge.EdgeEnrollmentException;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin / PSP REST API for the edge fleet — the session-authenticated counterpart to
 * {@link EdgeDistributionController}, and the API the fleet UI is built on.
 *
 * <p>Lives under {@code /edge/nodes} (not the permit-all machine paths {@code /edge/bundle},
 * {@code /edge/metrics}, {@code /edge/enroll}), so it goes through the normal user authentication
 * chain.
 *
 * <h2>Authorisation</h2>
 * <ul>
 *   <li>Class level: {@code SUPER_ADMIN}, {@code ADMIN}, {@code PLATFORM_ADMIN} or
 *       {@code PSP_ADMIN} — matching how the other admin surfaces in this codebase are secured.</li>
 *   <li>{@code approve} / {@code reject}: platform roles only. Contract §7 is explicit that
 *       approval is a <b>platform admin</b> decision — a PSP cannot authorise its own node.</li>
 *   <li>Every read and write is additionally tenant-scoped by {@link PspIsolationService}: a PSP
 *       user can only see and manage nodes owned by its own PSP; a cross-tenant id yields 403.</li>
 * </ul>
 */
@RestController
@RequestMapping("/edge/nodes")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','PSP_ADMIN')")
public class EdgeAdminController {

    private static final Logger log = LoggerFactory.getLogger(EdgeAdminController.class);

    private final EdgeEnrollmentService enrollmentService;
    private final EdgeMetricsRecordRepository metricsRepository;
    private final PspIsolationService pspIsolationService;

    public EdgeAdminController(EdgeEnrollmentService enrollmentService,
                               EdgeMetricsRecordRepository metricsRepository,
                               PspIsolationService pspIsolationService) {
        this.enrollmentService = enrollmentService;
        this.metricsRepository = metricsRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /** Fleet listing. PSP users always see exactly their own nodes, whatever {@code pspId} says. */
    @GetMapping
    public ResponseEntity<List<EdgeNodeView>> list(@RequestParam(required = false) Long pspId) {
        return ResponseEntity.ok(enrollmentService.listVisibleNodes(pspId).stream()
                .map(EdgeNodeView::of)
                .toList());
    }

    /**
     * Fleet analytics rollup over the aggregate counts the on-prem engines pushed.
     *
     * <p>Rule evaluation happens on customer premises; these summed counters are the control plane's
     * only view of what the fleet actually decided. Without this the pushed analytics were stored and
     * only ever readable one node at a time, so nothing consumed them in aggregate.
     *
     * <p>Tenant-scoped: a PSP user always gets exactly its own PSP's rollup regardless of the
     * {@code pspId} parameter; only a platform admin may request the cross-tenant view.
     *
     * @param days lookback window in days (default 7, clamped to 1..90)
     */
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> analytics(@RequestParam(required = false) Long pspId,
                                                         @RequestParam(defaultValue = "7") int days) {
        int lookback = Math.max(1, Math.min(90, days));
        Instant to = Instant.now();
        Instant from = to.minus(lookback, ChronoUnit.DAYS);

        // getCurrentUserPspId() returns 0L for a platform admin and the real id for a PSP user, so a
        // PSP user can never widen its scope by passing someone else's pspId.
        Long callerPspId = pspIsolationService.getCurrentUserPspId();
        Long scoped = (callerPspId == null || callerPspId == 0L) ? pspId : callerPspId;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromInclusive", from);
        body.put("toExclusive", to);
        body.put("lookbackDays", lookback);

        if (scoped == null) {
            // Platform-wide view, broken down per tenant.
            List<Map<String, Object>> perPsp = metricsRepository.summarizeAllPsps(from, to).stream()
                    .map(r -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("pspId", r[0]);
                        row.put("nodes", r[1]);
                        row.put("totalEvaluated", r[2]);
                        row.put("allowed", r[3]);
                        row.put("alerted", r[4]);
                        row.put("held", r[5]);
                        row.put("blocked", r[6]);
                        return row;
                    })
                    .toList();
            body.put("scope", "PLATFORM");
            body.put("perPsp", perPsp);
            return ResponseEntity.ok(body);
        }

        Object[] s = metricsRepository.summarizeForPsp(scoped, from, to);
        // An aggregate query always returns one row; guard anyway so an empty window reports zeros
        // rather than throwing (and is never mistaken for "no traffic recorded").
        body.put("scope", "PSP");
        body.put("pspId", scoped);
        body.put("nodes", s != null ? s[0] : 0L);
        body.put("totalEvaluated", s != null ? s[1] : 0L);
        body.put("allowed", s != null ? s[2] : 0L);
        body.put("alerted", s != null ? s[3] : 0L);
        body.put("held", s != null ? s[4] : 0L);
        body.put("blocked", s != null ? s[5] : 0L);
        body.put("avgP95LatencyMicros", s != null ? s[6] : 0.0);
        return ResponseEntity.ok(body);
    }

    /** Node detail plus health / last-seen and its most recent aggregate metrics windows. */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable Long id) {
        EdgeNode node = enrollmentService.getNode(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("node", EdgeNodeView.of(node));
        body.put("recentMetrics", metricsRepository.findTop20ByEdgeNodeIdOrderByWindowEndDesc(id));
        return ResponseEntity.ok(body);
    }

    /**
     * Request a node. Returns the raw enrollment code <b>once</b> — it is stored only as a SHA-256
     * digest and can never be shown again.
     */
    @PostMapping
    public ResponseEntity<EdgeNodeCreatedResponse> request(@RequestBody EdgeNodeRequestBody body) {
        Long pspId = pspIsolationService.sanitizePspId(body.pspId());
        if (pspId == null) {
            throw EdgeEnrollmentException.badRequest(
                    "pspId is required (platform administrators must name the owning PSP)");
        }
        EdgeEnrollmentService.EnrollmentRequestResult result =
                enrollmentService.requestNode(pspId, body.displayName(), currentUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(EdgeNodeCreatedResponse.of(
                EdgeNodeView.of(result.node()), result.rawEnrollmentCode(), result.expiresAt()));
    }

    /** PENDING → APPROVED. Platform decision (contract §7). */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<EdgeNodeView> approve(@PathVariable Long id) {
        return ResponseEntity.ok(EdgeNodeView.of(enrollmentService.approve(id, currentUsername())));
    }

    /** PENDING → REJECTED (terminal). Platform decision (contract §7). */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<EdgeNodeView> reject(@PathVariable Long id) {
        return ResponseEntity.ok(EdgeNodeView.of(enrollmentService.reject(id, currentUsername())));
    }

    /** ACTIVE → SUSPENDED. Distribution stops on the node's next poll. */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<EdgeNodeView> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(EdgeNodeView.of(enrollmentService.suspend(id, currentUsername())));
    }

    /** Irreversible revocation. Distribution stops immediately. */
    @PostMapping("/{id}/revoke")
    public ResponseEntity<EdgeNodeView> revoke(@PathVariable Long id) {
        return ResponseEntity.ok(EdgeNodeView.of(enrollmentService.revoke(id, currentUsername())));
    }

    // ── error mapping ────────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(EdgeEnrollmentException.class)
    public ResponseEntity<Map<String, String>> handleEnrollment(EdgeEnrollmentException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }

    /** {@link PspIsolationService} signals a cross-tenant access attempt with a SecurityException. */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleIsolation(SecurityException e) {
        log.warn("Edge fleet access denied: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
    }

    private String currentUsername() {
        User user = pspIsolationService.getCurrentUser();
        return user == null ? "unknown" : user.getUsername();
    }
}
