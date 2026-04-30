package com.posgateway.aml.controller;

import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.repository.AuditLogRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit Log Controller
 * Security: Only auditors and admins can access audit logs
 */
// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/audit/logs")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'MLRO')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/entity")
    @PreAuthorize("hasAuthority('VIEW_AUDIT_LOGS')")
    public ResponseEntity<List<AuditLog>> byEntity(@RequestParam String entityType,
            @RequestParam String entityId) {
        return ResponseEntity
                .ok(auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId));
    }

    @GetMapping("/user/{username}")
    @PreAuthorize("hasAuthority('VIEW_AUDIT_LOGS')")
    public ResponseEntity<List<AuditLog>> byUser(@PathVariable String username) {
        return ResponseEntity.ok(auditLogRepository.findByUsernameOrderByTimestampDesc(username));
    }

    @GetMapping("/range")
    @PreAuthorize("hasAuthority('VIEW_AUDIT_LOGS')")
    public ResponseEntity<List<AuditLog>> byRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(auditLogRepository.findByTimestampBetween(start, end));
    }

    /**
     * Get all audit logs
     */
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_AUDIT_LOGS')")
    public ResponseEntity<org.springframework.data.domain.Page<AuditLog>> getAllAuditLogs(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Long pspId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.posgateway.aml.entity.User currentUser) {

        int safeSize = Math.max(1, Math.min(size, 100)); // Max 100 per page to be safe
        int safePage = Math.max(0, page);

        Specification<AuditLog> spec = Specification.where(null);

        // PSP Isolation Logic
        if (currentUser != null) {
            Long userPspId = (currentUser.getPsp() != null) ? currentUser.getPsp().getPspId() : 0L;
            
            if (userPspId == 0L) {
                // Super Admin: Can filter by specific PSP or see all (if pspId is null)
                if (pspId != null) {
                    spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), pspId));
                }
            } else {
                // PSP User: Can ONLY see own PSP data
                // Ignore requested pspId param and force own pspId
                spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), userPspId));
            }
        }

        if (username != null && !username.isBlank()) {
            String u = username.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("username")), "%" + u + "%"));
        }
        if (actionType != null && !actionType.isBlank()) {
            String a = actionType.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("actionType")), a));
        }
        if (entityType != null && !entityType.isBlank()) {
            String e = entityType.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("entityType")), e));
        }
        if (entityId != null && !entityId.isBlank()) {
            String eid = entityId.trim();
            spec = spec.and((root, query, cb) -> cb.like(root.get("entityId"), "%" + eid + "%"));
        }
        if (success != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("success"), success));
        }
        if (ipAddress != null && !ipAddress.isBlank()) {
            String ip = ipAddress.trim();
            spec = spec.and((root, query, cb) -> cb.like(root.get("ipAddress"), "%" + ip + "%"));
        }
        if (sessionId != null && !sessionId.isBlank()) {
            String sid = sessionId.trim();
            spec = spec.and((root, query, cb) -> cb.like(root.get("sessionId"), "%" + sid + "%"));
        }
        if (start != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), start));
        }
        if (end != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), end));
        } 

        org.springframework.data.domain.Page<AuditLog> pageResult = auditLogRepository
                .findAll(spec, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp")));

        return ResponseEntity.ok(pageResult);
    }
}
