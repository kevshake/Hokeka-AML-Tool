package com.posgateway.aml.controller.residency;

import com.posgateway.aml.service.backfill.BackfillRun;
import com.posgateway.aml.service.backfill.BackfillRunStore;
import com.posgateway.aml.service.backfill.LedgerBackfillService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operator / admin API of the legacy back-fill to the Edge (plan R15, contract §11). Access rules
 * are in {@code LedgerBackfillAccess}: start and resume are platform-initiated and need the PSP's
 * support access plus a reason; pause, abort, row decisions and the report are also open to the
 * PSP's own administrators and compliance roles. Every action is audited.
 *
 * <table>
 *   <caption>Endpoints (under /api/v1/psps/{pspId}/edge-backfill)</caption>
 *   <tr><th>Method</th><th>Path</th><th>Purpose</th></tr>
 *   <tr><td>GET</td><td>(root)</td><td>per-PSP report: runs, counts, purge readiness</td></tr>
 *   <tr><td>POST</td><td>/runs</td><td>start ({@code defaultSiteId}, {@code reason})</td></tr>
 *   <tr><td>POST</td><td>/runs/{id}/pause | /resume | /abort</td><td>{@code reason}</td></tr>
 *   <tr><td>GET</td><td>/runs/{id}/rejections</td><td>rejected rows (legacy ids, codes)</td></tr>
 *   <tr><td>POST</td><td>/runs/{id}/rejections/exclude</td><td>operator decision on rejected rows</td></tr>
 * </table>
 *
 * Reads take the platform reason as {@code X-Support-Reason} (or {@code supportReason}); writes
 * take it in the body. Responses carry ids, counts and codes only.
 */
@RestController
@RequestMapping("/psps/{pspId}/edge-backfill")
@PreAuthorize("isAuthenticated()")
public class LedgerBackfillController {

    private final LedgerBackfillService service;

    /** Wires the controller. */
    public LedgerBackfillController(LedgerBackfillService service) {
        this.service = service;
    }

    /** Body of pause / resume / abort. */
    public record ReasonBody(String reason) {}

    /** Body of a row decision. */
    public record ExcludeBody(List<String> legacyTransactionIds, String reason) {}

    /** The PSP's back-fill report. */
    @GetMapping
    public LedgerBackfillService.PspReport report(@PathVariable long pspId,
                                                  @RequestParam(required = false) String supportReason,
                                                  @RequestHeader(value = "X-Support-Reason", required = false) String header) {
        return service.report(pspId, reason(supportReason, header));
    }

    /** Starts a back-fill (one run per receiving site). */
    @PostMapping("/runs")
    public ResponseEntity<List<BackfillRun>> start(@PathVariable long pspId,
                                                   @RequestBody LedgerBackfillService.StartRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.start(pspId, body));
    }

    /** Pauses a run. */
    @PostMapping("/runs/{backfillId}/pause")
    public BackfillRun pause(@PathVariable long pspId, @PathVariable UUID backfillId, @RequestBody ReasonBody body) {
        return service.pause(pspId, backfillId, body == null ? null : body.reason());
    }

    /** Resumes a run. */
    @PostMapping("/runs/{backfillId}/resume")
    public BackfillRun resume(@PathVariable long pspId, @PathVariable UUID backfillId, @RequestBody ReasonBody body) {
        return service.resume(pspId, backfillId, body == null ? null : body.reason());
    }

    /** Aborts a run. */
    @PostMapping("/runs/{backfillId}/abort")
    public BackfillRun abort(@PathVariable long pspId, @PathVariable UUID backfillId, @RequestBody ReasonBody body) {
        return service.abort(pspId, backfillId, body == null ? null : body.reason());
    }

    /** Rejected rows of a run. */
    @GetMapping("/runs/{backfillId}/rejections")
    public List<BackfillRunStore.RowRecord> rejections(@PathVariable long pspId, @PathVariable UUID backfillId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "100") int size,
                                                       @RequestParam(required = false) String supportReason,
                                                       @RequestHeader(value = "X-Support-Reason", required = false)
                                                       String header) {
        return service.rejections(pspId, backfillId, page, size, reason(supportReason, header));
    }

    /** Operator decision: the named rejected rows are not copied and may be purged. */
    @PostMapping("/runs/{backfillId}/rejections/exclude")
    public Map<String, Integer> exclude(@PathVariable long pspId, @PathVariable UUID backfillId,
                                        @RequestBody ExcludeBody body) {
        int n = service.exclude(pspId, backfillId, body == null ? null : body.legacyTransactionIds(),
                body == null ? null : body.reason());
        return Map.of("excluded", n);
    }

    /** The reason from the parameter, else the header. */
    private static String reason(String param, String header) {
        return param != null && !param.isBlank() ? param : header;
    }
}
