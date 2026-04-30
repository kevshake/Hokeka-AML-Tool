package com.posgateway.aml.controller;

import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.model.AlertDisposition;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.alert.AlertDispositionService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Alert Controller
 * Provides endpoints for fetching alerts with PSP-based filtering
 */
@RestController
@RequestMapping("/alerts")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR', 'ANALYST')")
@SuppressWarnings("null") // PathVariable Long parameters and repository Optional returns are safe
public class AlertController {

    private final AlertRepository alertRepository;
    private final AlertDispositionService alertDispositionService;
    private final PspIsolationService pspIsolationService;
    private final MerchantRepository merchantRepository;

    @Autowired
    public AlertController(AlertRepository alertRepository, AlertDispositionService alertDispositionService, PspIsolationService pspIsolationService, MerchantRepository merchantRepository) {
        this.alertRepository = alertRepository;
        this.alertDispositionService = alertDispositionService;
        this.pspIsolationService = pspIsolationService;
        this.merchantRepository = merchantRepository;
    }

    /**
     * Get all alerts filtered by PSP ID with pagination
     * GET /alerts
     * Super Admin (PSP ID 0) can see all alerts, PSP users see only their PSP's alerts
     * 
     * @param page Page number (default: 0)
     * @param size Page size (default: 25, max: 100)
     * @param status Optional status filter (OPEN, INVESTIGATING, RESOLVED)
     * @return Paginated list of alerts
     */
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<Alert>> getAllAlerts(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int size,
            @RequestParam(required = false) String status) {
        
        int safeSize = Math.max(1, Math.min(size, 100)); // Max 100 per page
        int safePage = Math.max(0, page);
        
        Long userPspId = pspIsolationService.getCurrentUserPspId();
        
        // Build Specification for filtering
        org.springframework.data.jpa.domain.Specification<Alert> spec = 
            org.springframework.data.jpa.domain.Specification.where(null);
        
        // PSP Isolation Logic
        if (userPspId != null && userPspId == 0L) {
            // Super Admin: Can see all alerts (no PSP filter)
            // Optional status filter
            if (status != null && !status.isBlank()) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
            }
        } else {
            // PSP User: Filter by PSP ID through merchants
            // Use a subquery to check if the alert's merchant belongs to the user's PSP
            spec = spec.and((root, query, cb) -> {
                var subquery = query.subquery(Long.class);
                var merchantRoot = subquery.from(com.posgateway.aml.entity.merchant.Merchant.class);
                subquery.select(merchantRoot.get("merchantId"))
                        .where(cb.and(
                            cb.equal(merchantRoot.get("merchantId"), root.get("merchantId")),
                            cb.equal(merchantRoot.get("psp").get("pspId"), userPspId)
                        ));
                return cb.exists(subquery);
            });
            
            // Optional status filter
            if (status != null && !status.isBlank()) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
            }
        }
        
        // Create Pageable with sorting by created date descending
        org.springframework.data.domain.Pageable pageable = 
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // Execute query with pagination
        org.springframework.data.domain.Page<Alert> pageResult = 
            alertRepository.findAll(spec, pageable);
        
        return ResponseEntity.ok(pageResult);
    }

    /**
     * Get alert by ID with PSP access validation
     * GET /alerts/{id}
     */
    @GetMapping("/{id}")
    @SuppressWarnings("null")
    public ResponseEntity<?> getAlertById(@PathVariable Long id) {
        Long userPspId = pspIsolationService.getCurrentUserPspId();
        
        return alertRepository.findById(id)
                .map(alert -> {
                    // Super Admin (PSP ID 0) can see all alerts
                    if (userPspId != null && userPspId == 0L) {
                        return ResponseEntity.ok(alert);
                    }
                    
                    // PSP User - validate that alert's merchant belongs to their PSP
                    if (alert.getMerchantId() != null) {
                        return merchantRepository.findById(alert.getMerchantId())
                                .map(merchant -> {
                                    if (merchant.getPsp() != null && merchant.getPsp().getPspId().equals(userPspId)) {
                                        return ResponseEntity.ok(alert);
                                    } else {
                                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
                                    }
                                })
                                .orElse(ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build());
                    } else {
                        // Alert without merchant - only Super Admin can access
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get count of active alerts filtered by PSP ID
     * GET /alerts/count/active
     * Super Admin (PSP ID 0) sees all alerts, PSP users see only their PSP's alerts
     */
    @GetMapping("/count/active")
    public ResponseEntity<Map<String, Long>> getActiveAlertCount() {
        Long userPspId = pspIsolationService.getCurrentUserPspId();
        Long count;
        
        if (userPspId != null && userPspId == 0L) {
            // Super Admin - count all open alerts
            count = alertRepository.countByStatus("open");
        } else {
            // PSP User - count open alerts for this PSP
            List<Alert> openAlerts = alertRepository.findByStatusAndPspId("open", userPspId);
            count = (long) openAlerts.size();
        }
        
        Map<String, Long> response = new HashMap<>();
        response.put("count", count != null ? count : 0L);
        return ResponseEntity.ok(response);
    }

    /**
     * Resolve an alert
     * PUT /alerts/{id}/resolve
     */
    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'INVESTIGATOR')")
    @SuppressWarnings("null")
    public ResponseEntity<?> resolveAlert(
            @PathVariable Long id,
            @RequestBody(required = false) ResolveAlertRequest request) {
        Long userPspId = pspIsolationService.getCurrentUserPspId();
        
        return alertRepository.findById(id)
                .map(alert -> {
                    // Validate PSP access for PSP users
                    if (userPspId != null && userPspId != 0L && alert.getMerchantId() != null) {
                        boolean hasAccess = merchantRepository.findById(alert.getMerchantId())
                                .map(merchant -> merchant.getPsp() != null && merchant.getPsp().getPspId().equals(userPspId))
                                .orElse(false);
                        if (!hasAccess) {
                            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
                        }
                    }
                    
                    // Set status based on disposition or default to resolved
                    if (request != null && request.getDisposition() != null) {
                        alert.setDisposition(request.getDisposition());
                        // Map disposition to status
                        AlertDisposition disp = request.getDisposition();
                        if (disp != null) {
                            if (disp.isFalsePositive()) {
                                alert.setStatus("false_positive");
                            } else if (disp.isTruePositive()) {
                                alert.setStatus("closed");
                            } else if (disp == AlertDisposition.ESCALATED) {
                                alert.setStatus("escalated");
                            } else {
                                alert.setStatus("resolved");
                            }
                        } else {
                            alert.setStatus("resolved");
                        }
                        alert.setDispositionReason(request.getNotes());
                        alert.setDisposedAt(java.time.LocalDateTime.now());
                        // Get current user for disposedBy
                        org.springframework.security.core.Authentication auth = 
                            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                        if (auth != null) {
                            alert.setDisposedBy(auth.getName());
                        }
                    } else {
                        // Default behavior if no request body
                        alert.setStatus("resolved");
                    }
                    
                    // Set notes if provided
                    if (request != null && request.getNotes() != null) {
                        alert.setNotes(request.getNotes());
                    }
                    
                    Alert saved = alertRepository.save(alert);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Request DTO for alert resolution
     */
    public static class ResolveAlertRequest {
        private com.posgateway.aml.model.AlertDisposition disposition;
        private String notes;

        public com.posgateway.aml.model.AlertDisposition getDisposition() {
            return disposition;
        }

        public void setDisposition(com.posgateway.aml.model.AlertDisposition disposition) {
            this.disposition = disposition;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    /**
     * Delete an alert
     * DELETE /alerts/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER')")
    @SuppressWarnings("null")
    public ResponseEntity<?> deleteAlert(@PathVariable Long id) {
        Long userPspId = pspIsolationService.getCurrentUserPspId();
        
        return alertRepository.findById(id)
                .map(alert -> {
                    // Validate PSP access for PSP users
                    if (userPspId != null && userPspId != 0L && alert.getMerchantId() != null) {
                        boolean hasAccess = merchantRepository.findById(alert.getMerchantId())
                                .map(merchant -> merchant.getPsp() != null && merchant.getPsp().getPspId().equals(userPspId))
                                .orElse(false);
                        if (!hasAccess) {
                            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
                        }
                    }
                    
                    alertRepository.deleteById(id);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get alert disposition statistics
     * GET /alerts/disposition-stats
     */
    @GetMapping("/disposition-stats")
    public ResponseEntity<Map<String, Object>> getDispositionStats(
            @RequestParam(required = false) Integer days) {
        int daysBack = days != null ? days : 30;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysBack);
        LocalDateTime endDate = LocalDateTime.now();
        
        AlertDispositionService.AlertDispositionStats stats = 
            alertDispositionService.getDispositionStatistics(startDate, endDate);
        Map<AlertDisposition, Long> distribution = 
            alertDispositionService.getDispositionDistribution(startDate, endDate);
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalAlerts", stats.getTotalAlerts());
        response.put("falsePositives", stats.getFalsePositives());
        response.put("truePositives", stats.getTruePositives());
        response.put("sarFiled", stats.getSarFiled());
        response.put("escalated", stats.getEscalated());
        response.put("falsePositiveRate", stats.getFalsePositiveRate());
        
        // Convert enum keys to strings for JSON
        Map<String, Long> distributionMap = distribution.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().name(),
                Map.Entry::getValue
            ));
        response.put("distribution", distributionMap);
        
        return ResponseEntity.ok(response);
    }
}
