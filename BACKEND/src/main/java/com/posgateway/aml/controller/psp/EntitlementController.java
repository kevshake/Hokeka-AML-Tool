package com.posgateway.aml.controller.psp;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.service.psp.EntitlementService;
import com.posgateway.aml.service.psp.QuotaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes the authenticated PSP's plan entitlements and current usage so the self-service portal can
 * show plan, feature set, monthly quota and consumption without any other endpoint leaking a
 * different tenant's data. Scope is implicit: it only ever reports the caller's own PSP.
 */
@RestController
@RequestMapping("/entitlements")
@PreAuthorize("isAuthenticated()")
public class EntitlementController {

    private final EntitlementService entitlementService;
    private final QuotaService quotaService;

    public EntitlementController(EntitlementService entitlementService, QuotaService quotaService) {
        this.entitlementService = entitlementService;
        this.quotaService = quotaService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> myEntitlements(@AuthenticationPrincipal User user) {
        Long pspId = user != null && user.getPsp() != null ? user.getPsp().getPspId() : null;
        EntitlementService.Entitlements ent = entitlementService.forPsp(pspId);
        long used = quotaService.currentMonthlyUsage(pspId);
        Integer limit = ent.maxChecksPerMonth();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("planCode", ent.planCode());
        body.put("features", ent.features());
        body.put("maxChecksPerMonth", limit);
        body.put("usedThisMonth", used);
        body.put("unlimited", ent.unlimited());
        body.put("remaining", (limit == null || limit <= 0) ? null : Math.max(0, limit - used));
        return ResponseEntity.ok(body);
    }
}
