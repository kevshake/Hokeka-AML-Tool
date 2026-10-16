package com.posgateway.aml.controller.risk;

import com.posgateway.aml.entity.security.PaymentBlacklistEntry;
import com.posgateway.aml.service.security.PaymentBlacklistAdminService;
import com.posgateway.aml.service.security.PaymentBlacklistAdminService.CreateRequest;
import com.posgateway.aml.service.security.PaymentBlacklistAdminService.UpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST API for the payment blacklist (PAN hash, terminal, IP, device, payment details, wallet).
 *
 * <pre>
 * GET    /api/v1/blacklist?pspId=&platformWide=&type=&active=&page=&size=
 * GET    /api/v1/blacklist/{id}
 * POST   /api/v1/blacklist          {entryType, value, reason, expiresAt?, pspId?, platformWide?}
 * PATCH  /api/v1/blacklist/{id}     {reason?, expiresAt?, clearExpiry?, active?}
 * DELETE /api/v1/blacklist/{id}?reason=
 * </pre>
 *
 * Tenancy and audit are enforced in {@link PaymentBlacklistAdminService}: PSP users see their PSP's
 * entries plus the platform-wide list and manage only their own; platform administrators manage all.
 * Reads are open to case-working roles; writes need a list-management role or MANAGE_WATCHLISTS.
 */
@RestController
@RequestMapping("/blacklist")
public class PaymentBlacklistController {

    private static final String READ_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','COMPLIANCE_OFFICER',"
            + "'INVESTIGATOR','PSP_ADMIN','PSP_USER') or hasAuthority('MANAGE_WATCHLISTS')";
    private static final String WRITE_ROLES = "hasAnyRole('SUPER_ADMIN','ADMIN','PLATFORM_ADMIN','COMPLIANCE_OFFICER',"
            + "'PSP_ADMIN') or hasAuthority('MANAGE_WATCHLISTS')";

    private final PaymentBlacklistAdminService service;

    public PaymentBlacklistController(PaymentBlacklistAdminService service) {
        this.service = service;
    }

    /** Paged list of entries visible to the caller, newest first. */
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public Page<PaymentBlacklistEntry> list(@RequestParam(required = false) Long pspId,
                                            @RequestParam(required = false) Boolean platformWide,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) Boolean active,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        int boundedSize = Math.max(1, Math.min(size, 200));
        return service.list(pspId, platformWide, type, active,
                PageRequest.of(Math.max(0, page), boundedSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    /** One entry (404 when missing or owned by another PSP). */
    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public PaymentBlacklistEntry get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Adds an entry; 409 when an active entry with the same scope, type and value already exists. */
    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PaymentBlacklistEntry> create(@RequestBody CreateRequest request, HttpServletRequest http) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, http.getRemoteAddr()));
        } catch (IllegalStateException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, duplicate.getMessage());
        }
    }

    /** Updates reason / expiry / active flag. */
    @PatchMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public PaymentBlacklistEntry update(@PathVariable Long id, @RequestBody UpdateRequest request,
                                        HttpServletRequest http) {
        return service.update(id, request, http.getRemoteAddr());
    }

    /** Removes (deactivates) an entry; the row is kept for the audit trail. */
    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Map<String, Object>> remove(@PathVariable Long id,
                                                      @RequestParam(required = false) String reason,
                                                      HttpServletRequest http) {
        service.remove(id, reason, http.getRemoteAddr());
        return ResponseEntity.ok(Map.of("id", id, "active", false));
    }
}
