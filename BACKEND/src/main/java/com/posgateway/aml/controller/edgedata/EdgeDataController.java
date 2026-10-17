package com.posgateway.aml.controller.edgedata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.service.edgedata.EdgeDataAccessLogService;
import com.posgateway.aml.service.edgedata.EdgeDataStatusService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Edge data residency views (work package R6):
 * <ul>
 *   <li>{@code GET /edge-data/status}: per Edge Site, tunnel state and ledger health;</li>
 *   <li>{@code GET /edge-data/access-log}: the PSP-visible log of every pull of its data (contract
 *       §7.15), server-side paginated and filtered;</li>
 *   <li>{@code GET /edge-data/access-log/export}: the same rows streamed as CSV or JSON lines.</li>
 * </ul>
 * PSP users always see their own PSP; platform staff name {@code pspId} (one PSP at a time).
 */
@RestController
@RequestMapping("/edge-data")
public class EdgeDataController {

    private static final String LOG_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','APP_CONTROLLER',"
            + "'PSP_ADMIN','COMPLIANCE_OFFICER','MLRO','AUDITOR')";

    private final EdgeDataStatusService status;
    private final EdgeDataAccessLogService accessLog;

    /** Wires the endpoints. */
    public EdgeDataController(EdgeDataStatusService status, EdgeDataAccessLogService accessLog) {
        this.status = status;
        this.accessLog = accessLog;
    }

    /** {@code GET /edge-data/status}. Platform staff need support access for the ledger part. */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','APP_CONTROLLER','COMPLIANCE_OFFICER',"
            + "'ANALYST','PSP_ADMIN','PSP_ANALYST','PSP_USER','VIEWER')")
    public ResponseEntity<ObjectNode> status(@RequestParam(required = false) Long pspId,
                                             @RequestParam(required = false) String supportReason,
                                             @RequestHeader(value = "X-Support-Reason", required = false) String reasonHeader) {
        String reason = supportReason != null && !supportReason.isBlank() ? supportReason : reasonHeader;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status.status(pspId, reason));
    }

    /** {@code GET /edge-data/access-log}: newest first, {@code page}/{@code size} (≤ 200). */
    @GetMapping("/access-log")
    @PreAuthorize(LOG_ROLES)
    public ResponseEntity<ObjectNode> accessLog(@RequestParam(required = false) Long pspId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to,
                                                @RequestParam(required = false) String op,
                                                @RequestParam(required = false) String actorKind,
                                                @RequestParam(required = false) Boolean supportAccess,
                                                @RequestParam(required = false) String outcome) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accessLog.page(pspId,
                new EdgeDataAccessLogService.Filter(from, to, op, actorKind, supportAccess, outcome), page, size));
    }

    /** {@code GET /edge-data/access-log/export}: every matching row, CSV or JSON lines. */
    @GetMapping("/access-log/export")
    @PreAuthorize(LOG_ROLES)
    public void exportAccessLog(@RequestParam(required = false) Long pspId,
                                @RequestParam(required = false) String format,
                                @RequestParam(required = false) String from,
                                @RequestParam(required = false) String to,
                                @RequestParam(required = false) String op,
                                @RequestParam(required = false) String actorKind,
                                @RequestParam(required = false) Boolean supportAccess,
                                @RequestParam(required = false) String outcome,
                                HttpServletResponse response) {
        accessLog.export(pspId, new EdgeDataAccessLogService.Filter(from, to, op, actorKind, supportAccess, outcome),
                format, response);
    }
}
