package com.posgateway.aml.controller.residency;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.residency.EdgeLimit;
import com.posgateway.aml.entity.residency.EdgeMerchantBlocklistEntry;
import com.posgateway.aml.entity.residency.PspEdgeSettings;
import com.posgateway.aml.service.residency.EdgeConfigInputService;
import com.posgateway.aml.service.residency.EdgeConfigService;
import com.posgateway.aml.service.residency.PspDataSettingsAccess;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Console/API management of a PSP's Edge config inputs and a plain-JSON preview of the document the
 * PSP's Edges receive (sealed) from {@code GET /api/v1/edge/config}.
 *
 * <table>
 *   <caption>Endpoints (under /api/v1/psps/{pspId}/edge-config)</caption>
 *   <tr><th>Method</th><th>Path</th><th>Purpose</th></tr>
 *   <tr><td>GET</td><td>(root)</td><td>current document and configVersion</td></tr>
 *   <tr><td>GET / PUT</td><td>/settings</td><td>CTR and tunnel settings</td></tr>
 *   <tr><td>GET / POST</td><td>/limits</td><td>list / add Edge limits</td></tr>
 *   <tr><td>DELETE</td><td>/limits/{limitId}</td><td>retire a limit</td></tr>
 *   <tr><td>GET / POST</td><td>/merchant-blocklist</td><td>list / add merchant blocks</td></tr>
 *   <tr><td>DELETE</td><td>/merchant-blocklist/{merchantId}</td><td>lift a block</td></tr>
 * </table>
 *
 * <p>Reads: users of the PSP and platform operators. Writes: the PSP's admins and platform
 * operators ({@link PspDataSettingsAccess#requireConfigEditor}).
 */
@RestController
@RequestMapping("/psps/{pspId}/edge-config")
@PreAuthorize("isAuthenticated()")
public class EdgeConfigAdminController {

    private final EdgeConfigService configService;
    private final EdgeConfigInputService inputs;
    private final PspDataSettingsAccess access;

    public EdgeConfigAdminController(EdgeConfigService configService,
                                     EdgeConfigInputService inputs,
                                     PspDataSettingsAccess access) {
        this.configService = configService;
        this.inputs = inputs;
        this.access = access;
    }

    /** The current (unsealed) Edge config document of the PSP. */
    @GetMapping
    public ObjectNode document(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return configService.current(pspId).document();
    }

    /** CTR and tunnel settings. */
    @GetMapping("/settings")
    public PspEdgeSettings settings(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return inputs.settings(pspId);
    }

    /** Updates CTR and tunnel settings (null members unchanged). */
    @PutMapping("/settings")
    public PspEdgeSettings updateSettings(@PathVariable Long pspId,
                                          @RequestBody EdgeConfigInputService.SettingsUpdate body) {
        User editor = access.requireConfigEditor(pspId);
        return inputs.updateSettings(pspId, body, editor);
    }

    /** Active Edge limits. */
    @GetMapping("/limits")
    public List<EdgeLimit> limits(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return inputs.limits(pspId);
    }

    /** Adds an Edge limit. */
    @PostMapping("/limits")
    public ResponseEntity<EdgeLimit> addLimit(@PathVariable Long pspId,
                                              @RequestBody EdgeConfigInputService.LimitRequest body) {
        User editor = access.requireConfigEditor(pspId);
        return ResponseEntity.status(HttpStatus.CREATED).body(inputs.addLimit(pspId, body, editor));
    }

    /** Retires an Edge limit. */
    @DeleteMapping("/limits/{limitId}")
    public ResponseEntity<Void> retireLimit(@PathVariable Long pspId, @PathVariable Long limitId) {
        User editor = access.requireConfigEditor(pspId);
        inputs.retireLimit(pspId, limitId, editor);
        return ResponseEntity.noContent().build();
    }

    /** Active merchant blocks. */
    @GetMapping("/merchant-blocklist")
    public List<EdgeMerchantBlocklistEntry> blocklist(@PathVariable Long pspId) {
        access.requireRead(pspId);
        return inputs.blocklist(pspId);
    }

    /** Blocks a merchant on the PSP's Edges. */
    @PostMapping("/merchant-blocklist")
    public ResponseEntity<EdgeMerchantBlocklistEntry> block(@PathVariable Long pspId,
                                                            @RequestBody EdgeConfigInputService.BlockRequest body) {
        User editor = access.requireConfigEditor(pspId);
        return ResponseEntity.status(HttpStatus.CREATED).body(inputs.block(pspId, body, editor));
    }

    /** Lifts a merchant block. */
    @DeleteMapping("/merchant-blocklist/{merchantId}")
    public ResponseEntity<Void> unblock(@PathVariable Long pspId, @PathVariable String merchantId) {
        User editor = access.requireConfigEditor(pspId);
        inputs.unblock(pspId, merchantId, editor);
        return ResponseEntity.noContent().build();
    }
}
