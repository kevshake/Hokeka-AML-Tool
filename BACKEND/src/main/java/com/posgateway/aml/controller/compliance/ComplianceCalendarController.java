package com.posgateway.aml.controller.compliance;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.compliance.ComplianceCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Compliance Calendar Controller
 * Manages regulatory filing deadlines
 */
@RestController
@RequestMapping("/compliance/calendar")
@PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER', 'MLRO', 'PSP_ADMIN')")
public class ComplianceCalendarController {

    private final ComplianceCalendarService complianceCalendarService;
    private final UserRepository userRepository;

    @Autowired
    public ComplianceCalendarController(ComplianceCalendarService complianceCalendarService,
                                        UserRepository userRepository) {
        this.complianceCalendarService = complianceCalendarService;
        this.userRepository = userRepository;
    }

    /**
     * pspId of the current authenticated user, or null for platform admins (psp == null)
     * which means "see everything including all PSPs' deadlines".
     */
    private Long currentPspId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null || user.getPsp() == null) return null;
        return user.getPsp().getPspId();
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ComplianceDeadline>> getUpcomingDeadlines(
            @RequestParam(defaultValue = "30") int daysAhead) {
        return ResponseEntity.ok(complianceCalendarService.getUpcomingDeadlines(daysAhead, currentPspId()));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<ComplianceDeadline>> getOverdueDeadlines() {
        return ResponseEntity.ok(complianceCalendarService.getOverdueDeadlines(currentPspId()));
    }

    @PostMapping("/deadlines")
    public ResponseEntity<ComplianceDeadline> createDeadline(@RequestBody CreateDeadlineRequest request) {
        return ResponseEntity.ok(complianceCalendarService.createDeadline(
                request.getDeadlineType(),
                request.getDeadlineDate(),
                request.getDescription(),
                request.getJurisdiction(),
                currentPspId()));
    }

    /**
     * Frontend-shape create endpoint matching the Compliance Calendar page form.
     * Body: { title, description?, dueDate (ISO-8601), deadlineType? }
     * Title is prepended to description so the existing entity (which has no title column)
     * still surfaces it in the UI.
     */
    @PostMapping
    public ResponseEntity<ComplianceDeadline> createDeadlineFrontendShape(
            @RequestBody CreateDeadlineFrontendRequest request) {
        String description = request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle() + (request.getDescription() != null ? " — " + request.getDescription() : "")
                : request.getDescription();
        String type = request.getDeadlineType() != null ? request.getDeadlineType() : "GENERIC";
        return ResponseEntity.ok(complianceCalendarService.createDeadline(
                type,
                request.getDueDate(),
                description,
                null,
                currentPspId()));
    }

    public static class CreateDeadlineFrontendRequest {
        private String title;
        private String description;
        private java.time.LocalDateTime dueDate;
        private String deadlineType;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public java.time.LocalDateTime getDueDate() { return dueDate; }
        public void setDueDate(java.time.LocalDateTime dueDate) { this.dueDate = dueDate; }
        public String getDeadlineType() { return deadlineType; }
        public void setDeadlineType(String deadlineType) { this.deadlineType = deadlineType; }
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
