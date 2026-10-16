package com.posgateway.aml.controller.residency;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.residency.PspDataSettingsAccess;
import com.posgateway.aml.service.residency.PspDataSettingsService;
import com.posgateway.aml.service.residency.PspDataSettingsService.ChangeView;
import com.posgateway.aml.service.residency.PspDataSettingsService.DataSettingsView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * PSP data residency settings (docs/EDGE_DATA_RESIDENCY_CONTRACT.md §8.1, §9, §10).
 *
 * <table>
 *   <caption>Endpoints (under /api/v1)</caption>
 *   <tr><th>Method</th><th>Path</th><th>Who</th></tr>
 *   <tr><td>GET</td><td>/psps/{id}/data-retention</td><td>users of the PSP, platform operators</td></tr>
 *   <tr><td>PUT</td><td>/psps/{id}/data-retention</td><td>PSP admin (maker) → 202 pending change</td></tr>
 *   <tr><td>GET / PUT</td><td>/psps/{id}/support-access</td><td>as above</td></tr>
 *   <tr><td>GET / PUT</td><td>/psps/{id}/cross-psp-opt-in</td><td>as above</td></tr>
 *   <tr><td>GET</td><td>/psps/{id}/data-settings/changes</td><td>users of the PSP, platform operators</td></tr>
 *   <tr><td>POST</td><td>/psps/{id}/data-settings/changes/{changeId}/approve | /reject</td>
 *       <td>PSP admin / MLRO / compliance officer other than the maker (checker)</td></tr>
 * </table>
 *
 * <p>Role and tenant checks live in {@link PspDataSettingsAccess}; a refusal is a
 * {@link SecurityException}, which the global handler answers with 403.
 */
@RestController
@RequestMapping("/psps/{pspId}")
@PreAuthorize("isAuthenticated()")
public class PspDataSettingsController {

    private final PspDataSettingsService service;
    private final PspDataSettingsAccess access;

    public PspDataSettingsController(PspDataSettingsService service, PspDataSettingsAccess access) {
        this.service = service;
        this.access = access;
    }

    /** Body of PUT /data-retention. */
    public record RetentionRequest(Integer retentionYears, String reason) {}

    /** Body of PUT /support-access. */
    public record SupportAccessRequest(Boolean enabled, String reason) {}

    /** Body of PUT /cross-psp-opt-in. */
    public record CrossPspRequest(Boolean optIn, String reason) {}

    /** Body of approve / reject. */
    public record ReviewRequest(String reason) {}

    /** Every data residency setting of the PSP plus its open change requests. */
    @GetMapping("/data-retention")
    public DataSettingsView retention(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return service.view(pspId);
    }

    /** Proposes a new retention period (5..25 years); a second person must approve it. */
    @PutMapping("/data-retention")
    public ResponseEntity<ChangeView> proposeRetention(@PathVariable Long pspId, @RequestBody RetentionRequest body) {
        User maker = access.requireMaker(pspId);
        ChangeView change = service.proposeRetention(pspId, body == null ? null : body.retentionYears(),
                body == null ? null : body.reason(), maker);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(change);
    }

    /** Same view as {@link #retention}; the Console's support-access panel reads it. */
    @GetMapping("/support-access")
    public DataSettingsView supportAccess(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return service.view(pspId);
    }

    /**
     * Enabling support access is proposed for approval (202); disabling takes effect at once (200).
     */
    @PutMapping("/support-access")
    public ResponseEntity<ChangeView> setSupportAccess(@PathVariable Long pspId, @RequestBody SupportAccessRequest body) {
        User maker = access.requireMaker(pspId);
        ChangeView change = service.proposeSupportAccess(pspId, body == null ? null : body.enabled(),
                body == null ? null : body.reason(), maker);
        return ResponseEntity.status(statusOf(change)).body(change);
    }

    /** Same view as {@link #retention}; the Console's cross-PSP panel reads it. */
    @GetMapping("/cross-psp-opt-in")
    public DataSettingsView crossPsp(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return service.view(pspId);
    }

    /** Opting in is proposed for approval (202); opting out takes effect at once (200). */
    @PutMapping("/cross-psp-opt-in")
    public ResponseEntity<ChangeView> setCrossPsp(@PathVariable Long pspId, @RequestBody CrossPspRequest body) {
        User maker = access.requireMaker(pspId);
        ChangeView change = service.proposeCrossPspOptIn(pspId, body == null ? null : body.optIn(),
                body == null ? null : body.reason(), maker);
        return ResponseEntity.status(statusOf(change)).body(change);
    }

    /** Change request history, newest first. */
    @GetMapping("/data-settings/changes")
    public List<ChangeView> changes(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return service.history(pspId);
    }

    /** Checker approval of a pending change. */
    @PostMapping("/data-settings/changes/{changeId}/approve")
    public ChangeView approve(@PathVariable Long pspId, @PathVariable Long changeId,
                              @RequestBody(required = false) ReviewRequest body) {
        User checker = access.requireChecker(pspId);
        return service.approve(pspId, changeId, body == null ? null : body.reason(), checker);
    }

    /** Checker rejection of a pending change. */
    @PostMapping("/data-settings/changes/{changeId}/reject")
    public ChangeView reject(@PathVariable Long pspId, @PathVariable Long changeId,
                             @RequestBody(required = false) ReviewRequest body) {
        User checker = access.requireChecker(pspId);
        return service.reject(pspId, changeId, body == null ? null : body.reason(), checker);
    }

    /** 200 when the change is already in force, 202 when it awaits approval. */
    private static HttpStatus statusOf(ChangeView change) {
        return "APPLIED".equals(change.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED;
    }
}
