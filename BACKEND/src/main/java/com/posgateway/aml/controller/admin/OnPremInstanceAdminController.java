package com.posgateway.aml.controller.admin;

import com.posgateway.aml.dto.onprem.OnPremApprovedDaysRequest;
import com.posgateway.aml.dto.onprem.OnPremInstanceCreateRequest;
import com.posgateway.aml.dto.onprem.OnPremInstanceCreatedResponse;
import com.posgateway.aml.dto.onprem.OnPremInstanceView;
import com.posgateway.aml.service.onprem.OnPremLeaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform-admin management of on-prem PSP service instances and lease policy.
 */
@RestController
@RequestMapping("/admin/onprem/instances")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_PLATFORM_ADMIN','MANAGE_PSP')")
public class OnPremInstanceAdminController {

    private final OnPremLeaseService leaseService;

    public OnPremInstanceAdminController(OnPremLeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    public ResponseEntity<OnPremInstanceCreatedResponse> create(
            @Valid @RequestBody OnPremInstanceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaseService.createInstance(request, currentActor()));
    }

    @GetMapping
    public ResponseEntity<List<OnPremInstanceView>> list(
            @RequestParam(required = false) Long pspId) {
        if (pspId != null) {
            return ResponseEntity.ok(leaseService.listByPsp(pspId));
        }
        return ResponseEntity.ok(leaseService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OnPremInstanceView> get(@PathVariable Long id) {
        return ResponseEntity.ok(leaseService.get(id));
    }

    @PutMapping("/{id}/approved-days")
    public ResponseEntity<OnPremInstanceView> setApprovedDays(
            @PathVariable Long id,
            @Valid @RequestBody OnPremApprovedDaysRequest request) {
        return ResponseEntity.ok(leaseService.setApprovedDays(id, request.approvedDays()));
    }

    @PutMapping("/{id}/revoke")
    public ResponseEntity<OnPremInstanceView> revoke(@PathVariable Long id) {
        return ResponseEntity.ok(leaseService.revoke(id, currentActor()));
    }

    @PutMapping("/{id}/suspend")
    public ResponseEntity<OnPremInstanceView> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(leaseService.suspend(id, currentActor()));
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "system";
    }
}
