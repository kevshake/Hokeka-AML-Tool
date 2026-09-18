package com.posgateway.aml.controller.admin;

import com.posgateway.aml.entity.psp.BillingRate;
import com.posgateway.aml.service.psp.BillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/psps/{pspId}/billing-rates")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PLATFORM_ADMIN')")
public class BillingRateAdminController {
    private final BillingService service;

    public BillingRateAdminController(BillingService service) {
        this.service = service;
    }

    @GetMapping
    public List<BillingRate> list(@PathVariable Long pspId) {
        return service.getRatesForPsp(pspId);
    }

    @PostMapping
    public ResponseEntity<BillingRate> create(@PathVariable Long pspId, @RequestBody BillingRate rate) {
        return ResponseEntity.ok(service.saveRateOverride(pspId, rate));
    }

    @PutMapping("/{rateId}")
    public ResponseEntity<BillingRate> update(@PathVariable Long pspId, @PathVariable Long rateId,
            @RequestBody BillingRate rate) {
        return ResponseEntity.ok(service.updateRateOverride(pspId, rateId, rate));
    }

    @DeleteMapping("/{rateId}")
    public ResponseEntity<BillingRate> deactivate(@PathVariable Long pspId, @PathVariable Long rateId) {
        return ResponseEntity.ok(service.deactivateRateOverride(pspId, rateId));
    }
}
