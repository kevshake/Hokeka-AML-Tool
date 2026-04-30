package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.service.compliance.ComplianceCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Compliance Calendar Controller
 * Manages regulatory filing deadlines
 */
@RestController
@RequestMapping("/compliance/calendar")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'MLRO')")
public class ComplianceCalendarController {

    private final ComplianceCalendarService complianceCalendarService;

    @Autowired
    public ComplianceCalendarController(ComplianceCalendarService complianceCalendarService) {
        this.complianceCalendarService = complianceCalendarService;
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ComplianceDeadline>> getUpcomingDeadlines(
            @RequestParam(defaultValue = "30") int daysAhead) {
        return ResponseEntity.ok(complianceCalendarService.getUpcomingDeadlines(daysAhead));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<ComplianceDeadline>> getOverdueDeadlines() {
        return ResponseEntity.ok(complianceCalendarService.getOverdueDeadlines());
    }

    @PostMapping("/deadlines")
    public ResponseEntity<ComplianceDeadline> createDeadline(@RequestBody CreateDeadlineRequest request) {
        return ResponseEntity.ok(complianceCalendarService.createDeadline(
                request.getDeadlineType(),
                request.getDeadlineDate(),
                request.getDescription(),
                request.getJurisdiction()));
    }

    @PostMapping("/deadlines/{deadlineId}/complete")
    public ResponseEntity<Void> markDeadlineCompleted(@PathVariable Long deadlineId) {
        complianceCalendarService.markDeadlineCompleted(deadlineId);
        return ResponseEntity.ok().build();
    }

    public static class CreateDeadlineRequest {
        private String deadlineType;
        private java.time.LocalDateTime deadlineDate;
        private String description;
        private String jurisdiction;

        // Getters and Setters
        public String getDeadlineType() {
            return deadlineType;
        }

        public void setDeadlineType(String deadlineType) {
            this.deadlineType = deadlineType;
        }

        public java.time.LocalDateTime getDeadlineDate() {
            return deadlineDate;
        }

        public void setDeadlineDate(java.time.LocalDateTime deadlineDate) {
            this.deadlineDate = deadlineDate;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getJurisdiction() {
            return jurisdiction;
        }

        public void setJurisdiction(String jurisdiction) {
            this.jurisdiction = jurisdiction;
        }
    }
}
