package com.posgateway.aml.controller.transaction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.dto.alert.TransactionAlertsResponseDTO;
import com.posgateway.aml.dto.assessment.AssessmentDetailResponseDTO;
import com.posgateway.aml.dto.compliance.ComplianceLinksResponse;
import com.posgateway.aml.dto.edgedata.EdgeDataBody;
import com.posgateway.aml.service.edgedata.EdgeDataCallerResolver;
import com.posgateway.aml.service.edgedata.TransactionExportService;
import com.posgateway.aml.service.edgedata.TransactionFilter;
import com.posgateway.aml.service.edgedata.TransactionLinksService;
import com.posgateway.aml.service.edgedata.TransactionReadService;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/**
 * Tunnel-backed transaction endpoints (work package R6, contract §7.14). The cloud stores no
 * transaction data: transaction fields are pulled live from the PSP's Edge over the Edge-opened
 * tunnel; alerts, cases and decision-record fields come from the cloud database by ID.
 *
 * <ul>
 *   <li>{@code GET /transactions}: {@code txn.search} (from/to window, ≤ 93 days, default the last
 *       30 days; cursor paging), envelope {@code {items, nextCursor, incomplete, unavailableSites,
 *       source}}.</li>
 *   <li>{@code GET /transactions/{id}}: {@code txn.get} (decision record id, or a legacy numeric id)
 *       plus the cloud {@code decisionRecord}.</li>
 *   <li>{@code POST /transactions:batchGet}: up to 200 decision record ids, request order,
 *       {@code missing}.</li>
 *   <li>{@code GET /transactions/{edgeAssessmentId}/related}: {@code txn.related} (depth 1..2).</li>
 *   <li>{@code GET /transactions/export}: {@code export.stream} written straight to the client as CSV
 *       or JSON lines, never stored.</li>
 *   <li>{@code GET /transactions/{id}/alerts}, {@code /assessment}, {@code /compliance-links}:
 *       cloud-held workflow data by ID.</li>
 * </ul>
 *
 * <p>The PSP comes from the principal for PSP users; platform staff name {@code pspId} and give a
 * {@code supportReason} (parameter or {@code X-Support-Reason} header) and are served only when the
 * PSP enabled support access. Every pull is audited in the PSP-visible access log. Tunnel failures
 * are §7.11 bodies with their status ({@code EdgeDataExceptionHandler}); every response is
 * {@code Cache-Control: no-store}.
 */
@RestController
public class EdgeTransactionController {

    private static final String READ_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','COMPLIANCE_OFFICER',"
            + "'INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_USER','PSP_ANALYST','VIEWER')";
    private static final String LIST_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','COMPLIANCE_OFFICER',"
            + "'ANALYST','PSP_ADMIN','PSP_ANALYST','VIEWER')";
    private static final String EXPORT_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','COMPLIANCE_OFFICER',"
            + "'ANALYST','PSP_ADMIN','PSP_ANALYST')";

    private final TransactionReadService reads;
    private final TransactionExportService exports;
    private final TransactionLinksService links;
    private final Clock clock = Clock.systemUTC();

    /** Wires the endpoints. */
    public EdgeTransactionController(TransactionReadService reads, TransactionExportService exports,
                                     TransactionLinksService links) {
        this.reads = reads;
        this.exports = exports;
        this.links = links;
    }

    /** The batchGet body: {@code {"edgeAssessmentIds": [...]}}. */
    public record BatchGetRequest(List<String> edgeAssessmentIds) {}

    /** Compliance links plus the transaction's source, serialised flat. */
    public record SourcedComplianceLinks(@JsonUnwrapped ComplianceLinksResponse links, String source) {}

    /** {@code GET /transactions}: one page of the PSP's transactions, newest first. */
    @GetMapping("/transactions")
    @PreAuthorize(LIST_ROLES)
    public ResponseEntity<EdgeDataBody> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Long pspId,
                                           @RequestParam(required = false) String supportReason,
                                           @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader,
                                           @RequestParam(required = false) String purpose,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) String merchantId,
                                           @RequestParam(required = false) List<String> decisions,
                                           @RequestParam(required = false) Long ruleId,
                                           @RequestParam(required = false) String txnRef,
                                           @RequestParam(required = false) Integer minScore,
                                           @RequestParam(required = false) Boolean ctrRequired,
                                           @RequestParam(required = false) Boolean sanctionsHit,
                                           @RequestParam(required = false) Long caseId,
                                           @RequestParam(required = false) Long alertId) {
        TransactionFilter filter = TransactionFilter.parse(from, to, merchantId, decisions, ruleId, txnRef, minScore,
                ctrRequired, sanctionsHit, clock);
        return noStore(reads.list(access(pspId, supportReason, reasonHeader, purpose, caseId, alertId), filter, size,
                cursor, page));
    }

    /** {@code GET /transactions/export}: streamed CSV ({@code format=csv}) or JSON lines ({@code ndjson}). */
    @GetMapping("/transactions/export")
    @PreAuthorize(EXPORT_ROLES)
    public void export(@RequestParam(required = false) String format,
                       @RequestParam(required = false) List<String> columns,
                       @RequestParam(required = false) Integer maxRows,
                       @RequestParam(required = false) Long pspId,
                       @RequestParam(required = false) String supportReason,
                       @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader,
                       @RequestParam(required = false) String purpose,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String merchantId,
                       @RequestParam(required = false) List<String> decisions,
                       @RequestParam(required = false) Long ruleId,
                       @RequestParam(required = false) String txnRef,
                       @RequestParam(required = false) Integer minScore,
                       @RequestParam(required = false) Boolean ctrRequired,
                       @RequestParam(required = false) Boolean sanctionsHit,
                       HttpServletResponse response) {
        TransactionFilter filter = TransactionFilter.parse(from, to, merchantId, decisions, ruleId, txnRef, minScore,
                ctrRequired, sanctionsHit, clock);
        exports.export(access(pspId, supportReason, reasonHeader, purpose, null, null), filter, format, columns,
                maxRows, response);
    }

    /** {@code POST /transactions:batchGet}: up to 200 transactions by decision record id. */
    @PostMapping("/transactions:batchGet")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<EdgeDataBody> batchGet(@RequestBody BatchGetRequest body,
                                               @RequestParam(required = false) Long pspId,
                                               @RequestParam(required = false) String supportReason,
                                               @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader,
                                               @RequestParam(required = false) String purpose,
                                               @RequestParam(required = false) Long caseId,
                                               @RequestParam(required = false) Long alertId) {
        return noStore(reads.batchGet(access(pspId, supportReason, reasonHeader, purpose, caseId, alertId),
                body == null ? null : body.edgeAssessmentIds()));
    }

    /** {@code GET /transactions/{id}}: the Edge detail and the cloud decision record. */
    @GetMapping("/transactions/{id}")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<EdgeDataBody> get(@PathVariable String id,
                                          @RequestParam(required = false) Long pspId,
                                          @RequestParam(required = false) String supportReason,
                                          @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader,
                                          @RequestParam(required = false) String purpose,
                                          @RequestParam(required = false) Long caseId,
                                          @RequestParam(required = false) Long alertId) {
        return noStore(reads.get(access(pspId, supportReason, reasonHeader, purpose, caseId, alertId), id));
    }

    /** {@code GET /transactions/{edgeAssessmentId}/related}: linked evaluations (never the key value). */
    @GetMapping("/transactions/{id}/related")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<EdgeDataBody> related(@PathVariable String id,
                                              @RequestParam(required = false) List<String> linkBy,
                                              @RequestParam(required = false) Integer windowDays,
                                              @RequestParam(required = false) Integer depth,
                                              @RequestParam(defaultValue = "50") int size,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Long pspId,
                                              @RequestParam(required = false) String supportReason,
                                              @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader,
                                              @RequestParam(required = false) String purpose,
                                              @RequestParam(required = false) Long caseId,
                                              @RequestParam(required = false) Long alertId) {
        return noStore(reads.related(access(pspId, supportReason, reasonHeader, purpose, caseId, alertId), id, linkBy,
                windowDays, depth, size, cursor));
    }

    /** {@code GET /transactions/{id}/alerts}: alerts on the transaction (cloud workflow data by ID). */
    @GetMapping("/transactions/{id}/alerts")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<TransactionAlertsResponseDTO> alerts(@PathVariable String id,
                                                                @RequestParam(required = false) Long pspId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.alerts(id, pspId));
    }

    /** {@code GET /transactions/{id}/assessment}: the decision record / assessment with its findings. */
    @GetMapping("/transactions/{id}/assessment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN',"
            + "'PSP_ANALYST','PSP_USER','VIEWER','PSP_INTEGRATION')")
    public ResponseEntity<AssessmentDetailResponseDTO> getTransactionAssessment(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.assessment(id));
    }

    /** {@code GET /transactions/{id}/compliance-links}: cases, SARs, disputes, screening by ID. */
    @GetMapping("/transactions/{id}/compliance-links")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST',"
            + "'PSP_ADMIN','PSP_USER')")
    public ResponseEntity<SourcedComplianceLinks> complianceLinks(@PathVariable String id,
                                                                  @RequestParam(required = false) Long pspId) {
        TransactionLinksService.SourcedLinks l = links.complianceLinks(id, pspId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new SourcedComplianceLinks(l.links(), l.source()));
    }

    /** The caller's request context; the PSP-user restrictions are applied by the scope resolver. */
    private static TransactionReadService.Access access(Long pspId, String supportReason, String reasonHeader,
                                                        String purpose, Long caseId, Long alertId) {
        String reason = supportReason != null && !supportReason.isBlank() ? supportReason : reasonHeader;
        EdgeDataCallerResolver.WorkflowRefs refs = new EdgeDataCallerResolver.WorkflowRefs(
                caseId == null ? null : String.valueOf(caseId), alertId == null ? null : String.valueOf(alertId), null);
        return new TransactionReadService.Access(pspId, reason, TransactionReadService.purpose(purpose), refs);
    }

    /** 200 with {@code Cache-Control: no-store}; the body's toString never shows the data. */
    private static ResponseEntity<EdgeDataBody> noStore(ObjectNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EdgeDataBody(body));
    }
}
