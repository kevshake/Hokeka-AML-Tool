package com.posgateway.aml.controller.admin;

import com.posgateway.aml.entity.admin.RuntimeError;
import com.posgateway.aml.service.admin.RuntimeErrorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read API for persisted server runtime errors ({@code runtime_errors} table).
 */
@RestController
@RequestMapping("/admin/runtime-errors")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class RuntimeErrorController {

    private final RuntimeErrorService runtimeErrorService;

    public RuntimeErrorController(RuntimeErrorService runtimeErrorService) {
        this.runtimeErrorService = runtimeErrorService;
    }

    @GetMapping
    public ResponseEntity<Page<RuntimeError>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        return ResponseEntity.ok(
                runtimeErrorService.list(PageRequest.of(Math.max(0, page), safeSize)));
    }
}
