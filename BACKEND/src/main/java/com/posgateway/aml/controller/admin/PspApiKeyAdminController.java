package com.posgateway.aml.controller.admin;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.security.PspApiKey;
import com.posgateway.aml.service.security.PspApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/psp-api-keys")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PLATFORM_ADMIN')")
public class PspApiKeyAdminController {
    private final PspApiKeyService service;

    public PspApiKeyAdminController(PspApiKeyService service) { this.service = service; }

    /** Lists metadata for PSP integration API keys scoped to the requested tenant (secrets are never returned). */
    @GetMapping
    public List<PspApiKey> list(@RequestParam Long pspId) { return service.list(pspId); }

    /** Issues a new PSP API key and returns the one-time plaintext secret to the platform operator. */
    @PostMapping
    public PspApiKeyService.IssuedApiKey create(
            @RequestParam Long pspId, @AuthenticationPrincipal User user) {
        return service.create(pspId, user == null ? null : user.getId());
    }

    /** Rotates an existing key id and returns a fresh one-time plaintext secret. */
    @PostMapping("/{id}/rotate")
    public PspApiKeyService.IssuedApiKey rotate(
            @PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.rotate(id, user == null ? null : user.getId());
    }

    /** Revokes a PSP API key so it can no longer authenticate integration traffic. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        service.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
