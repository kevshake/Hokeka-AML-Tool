package com.posgateway.aml.controller.admin;

import com.posgateway.aml.config.onprem.OnPremLeaseDeprecation;
import com.posgateway.aml.dto.onprem.OnPremApprovedDaysRequest;
import com.posgateway.aml.dto.onprem.OnPremInstanceCreateRequest;
import com.posgateway.aml.dto.onprem.OnPremInstanceView;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Former platform-admin enrollment for full-BACKEND on-prem lease instances.
 *
 * <p><b>Product path removed.</b> Enroll Edge Nodes via {@code /edge/nodes} ({@code EdgeAdminController}).
 */
@RestController
@RequestMapping("/admin/onprem/instances")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_PLATFORM_ADMIN','MANAGE_PSP')")
public class OnPremInstanceAdminController {

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody OnPremInstanceCreateRequest request) {
        return OnPremLeaseDeprecation.gone();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) Long pspId) {
        return ResponseEntity.ok(Map.of(
                "deprecated", true,
                "code", OnPremLeaseDeprecation.CODE,
                "message", OnPremLeaseDeprecation.MESSAGE,
                "replacementPath", "/edge/nodes",
                "instances", List.<OnPremInstanceView>of()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return OnPremLeaseDeprecation.gone();
    }

    @PutMapping("/{id}/approved-days")
    public ResponseEntity<?> setApprovedDays(
            @PathVariable Long id,
            @Valid @RequestBody OnPremApprovedDaysRequest request) {
        return OnPremLeaseDeprecation.gone();
    }

    @PutMapping("/{id}/revoke")
    public ResponseEntity<?> revoke(@PathVariable Long id) {
        return OnPremLeaseDeprecation.gone();
    }

    @PutMapping("/{id}/suspend")
    public ResponseEntity<?> suspend(@PathVariable Long id) {
        return OnPremLeaseDeprecation.gone();
    }
}
