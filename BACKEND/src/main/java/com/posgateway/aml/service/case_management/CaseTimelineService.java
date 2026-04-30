package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.compliance.CaseActivity;
import com.posgateway.aml.entity.compliance.CaseEvidence;
import com.posgateway.aml.entity.compliance.CaseTransaction;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.repository.CaseActivityRepository;
import com.posgateway.aml.repository.compliance.CaseEvidenceRepository;
import com.posgateway.aml.repository.CaseTransactionRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Case Timeline Service
 * Builds chronological timeline of events for a case
 */
@Service
public class CaseTimelineService {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(CaseTimelineService.class);

    private final ComplianceCaseRepository caseRepository;
    private final CaseActivityRepository activityRepository;
    private final CaseTransactionRepository caseTransactionRepository;
    private final CaseEvidenceRepository evidenceRepository;
    private final SuspiciousActivityReportRepository sarRepository;

    @Autowired
    public CaseTimelineService(ComplianceCaseRepository caseRepository,
                              CaseActivityRepository activityRepository,
                              CaseTransactionRepository caseTransactionRepository,
                              CaseEvidenceRepository evidenceRepository,
                              SuspiciousActivityReportRepository sarRepository) {
        this.caseRepository = caseRepository;
        this.activityRepository = activityRepository;
        this.caseTransactionRepository = caseTransactionRepository;
        this.evidenceRepository = evidenceRepository;
        this.sarRepository = sarRepository;
    }

    /**
     * Build timeline for a case
     */
    public CaseTimelineDTO buildTimeline(Long caseId) {
        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

        List<TimelineEvent> events = new ArrayList<>();

        // Case creation
        events.add(TimelineEvent.builder()
                .timestamp(complianceCase.getCreatedAt())
                .type("CASE_CREATED")
                .description("Case " + complianceCase.getCaseReference() + " created")
                .data(Map.of("caseReference", complianceCase.getCaseReference(), 
                             "description", complianceCase.getDescription() != null ? complianceCase.getDescription() : ""))
                .build());

        // Case assignments
        if (complianceCase.getAssignedAt() != null) {
            events.add(TimelineEvent.builder()
                    .timestamp(complianceCase.getAssignedAt())
                    .type("CASE_ASSIGNED")
                    .description("Assigned to " + 
                            (complianceCase.getAssignedTo() != null ? 
                                    complianceCase.getAssignedTo().getUsername() : "Unknown"))
                    .data(Map.of("assignedTo", complianceCase.getAssignedTo() != null ? 
                            complianceCase.getAssignedTo().getUsername() : "Unknown",
                            "assignedBy", complianceCase.getAssignedBy() != null ? complianceCase.getAssignedBy() : 0L))
                    .build());
        }

        // Status changes (tracked via updatedAt if status changed)
        if (complianceCase.getUpdatedAt() != null && 
            !complianceCase.getUpdatedAt().equals(complianceCase.getCreatedAt())) {
            events.add(TimelineEvent.builder()
                    .timestamp(complianceCase.getUpdatedAt())
                    .type("CASE_STATUS_CHANGED")
                    .description("Status changed to " + complianceCase.getStatus())
                    .data(Map.of("status", complianceCase.getStatus().name()))
                    .build());
        }

        // Priority tracking
        if (complianceCase.getPriority() != null) {
            events.add(TimelineEvent.builder()
                    .timestamp(complianceCase.getCreatedAt())
                    .type("CASE_PRIORITY_SET")
                    .description("Priority set to " + complianceCase.getPriority())
                    .data(Map.of("priority", complianceCase.getPriority().name()))
                    .build());
        }

        // SLA Deadline
        if (complianceCase.getSlaDeadline() != null) {
            events.add(TimelineEvent.builder()
                    .timestamp(complianceCase.getSlaDeadline())
                    .type("SLA_DEADLINE")
                    .description("SLA Deadline: " + complianceCase.getSlaDeadline())
                    .data(Map.of("deadline", complianceCase.getSlaDeadline().toString()))
                    .build());
        }

        // Related transactions
        List<CaseTransaction> caseTransactions = caseTransactionRepository.findByComplianceCase(complianceCase);
        caseTransactions.forEach(ct -> {
            if (ct.getTransaction() != null && ct.getTransaction().getTxnTs() != null) {
                events.add(TimelineEvent.builder()
                        .timestamp(ct.getTransaction().getTxnTs())
                        .type("TRANSACTION")
                        .description("Transaction: " + ct.getTransaction().getTxnId())
                        .data(ct.getTransaction())
                        .build());
            }
        });

        // Case notes
        if (complianceCase.getNotes() != null) {
            complianceCase.getNotes().forEach(note -> {
                events.add(TimelineEvent.builder()
                        .timestamp(note.getCreatedAt())
                        .type("NOTE")
                        .description("Note added by " + note.getAuthor().getUsername())
                        .data(Map.of("noteId", note.getId(),
                                    "content", note.getContent() != null ? note.getContent() : "",
                                    "author", note.getAuthor().getUsername(),
                                    "internal", note.isInternal()))
                        .build());
            });
        }

        // Evidence attachments
        List<CaseEvidence> evidenceList = evidenceRepository.findByComplianceCase_Id(caseId);
        evidenceList.forEach(evidence -> {
            events.add(TimelineEvent.builder()
                    .timestamp(evidence.getUploadedAt())
                    .type("EVIDENCE_ATTACHED")
                    .description("Evidence attached: " + evidence.getFileName() + 
                            (evidence.getDescription() != null ? " - " + evidence.getDescription() : ""))
                    .data(Map.of("evidenceId", evidence.getId(),
                                "fileName", evidence.getFileName(),
                                "fileType", evidence.getFileType(),
                                "uploadedBy", evidence.getUploadedBy() != null ? evidence.getUploadedBy().getUsername() : "Unknown",
                                "description", evidence.getDescription() != null ? evidence.getDescription() : ""))
                    .build());
        });

        // SARs related to this case
        List<SuspiciousActivityReport> sars = sarRepository.findAll().stream()
                .filter(sar -> sar.getComplianceCase() != null && sar.getComplianceCase().getId().equals(caseId))
                .collect(Collectors.toList());
        sars.forEach(sar -> {
            // SAR Creation
            if (sar.getCreatedAt() != null) {
                events.add(TimelineEvent.builder()
                        .timestamp(sar.getCreatedAt())
                        .type("SAR_CREATED")
                        .description("SAR " + sar.getSarReference() + " created")
                        .data(Map.of("sarId", sar.getId(),
                                    "sarReference", sar.getSarReference(),
                                    "status", sar.getStatus().name()))
                        .build());
            }
            
            // SAR Approval
            if (sar.getApprovedAt() != null) {
                events.add(TimelineEvent.builder()
                        .timestamp(sar.getApprovedAt())
                        .type("SAR_APPROVED")
                        .description("SAR " + sar.getSarReference() + " approved by " + 
                                (sar.getApprovedBy() != null ? sar.getApprovedBy().getUsername() : "Unknown"))
                        .data(Map.of("sarId", sar.getId(),
                                    "sarReference", sar.getSarReference(),
                                    "approvedBy", sar.getApprovedBy() != null ? sar.getApprovedBy().getUsername() : "Unknown"))
                        .build());
            }
            
            // SAR Filing
            if (sar.getFiledAt() != null) {
                events.add(TimelineEvent.builder()
                        .timestamp(sar.getFiledAt())
                        .type("SAR_FILED")
                        .description("SAR " + sar.getSarReference() + " filed with reference " + 
                                (sar.getFilingReferenceNumber() != null ? sar.getFilingReferenceNumber() : "N/A"))
                        .data(Map.of("sarId", sar.getId(),
                                    "sarReference", sar.getSarReference(),
                                    "filingReference", sar.getFilingReferenceNumber() != null ? sar.getFilingReferenceNumber() : "",
                                    "filedBy", sar.getFiledBy() != null ? sar.getFiledBy().getUsername() : "Unknown"))
                        .build());
            }
        });

        // Escalations
        if (complianceCase.getEscalatedAt() != null) {
            events.add(TimelineEvent.builder()
                    .timestamp(complianceCase.getEscalatedAt())
                    .type("ESCALATION")
                    .description("Case escalated: " + 
                            (complianceCase.getEscalationReason() != null ? complianceCase.getEscalationReason() : "No reason provided"))
                    .data(Map.of("escalatedTo", complianceCase.getEscalatedTo() != null ? complianceCase.getEscalatedTo() : 0L,
                                "reason", complianceCase.getEscalationReason() != null ? complianceCase.getEscalationReason() : ""))
                    .build());
        }

        // Resolution
        if (complianceCase.getResolvedAt() != null) {
            events.add(TimelineEvent.builder()
                    .timestamp(complianceCase.getResolvedAt())
                    .type("CASE_RESOLVED")
                    .description("Case resolved: " + 
                            (complianceCase.getResolution() != null ? complianceCase.getResolution() : "Unknown resolution"))
                    .data(Map.of("resolution", complianceCase.getResolution() != null ? complianceCase.getResolution() : "",
                                "resolutionNotes", complianceCase.getResolutionNotes() != null ? complianceCase.getResolutionNotes() : "",
                                "resolvedBy", complianceCase.getResolvedBy() != null ? complianceCase.getResolvedBy() : 0L))
                    .build());
        }

        // Related cases
        if (complianceCase.getRelatedCases() != null && !complianceCase.getRelatedCases().isEmpty()) {
            complianceCase.getRelatedCases().forEach(relatedCase -> {
                events.add(TimelineEvent.builder()
                        .timestamp(complianceCase.getCreatedAt()) // Use case creation time as default
                        .type("CASE_LINKED")
                        .description("Linked to case " + relatedCase.getCaseReference())
                        .data(Map.of("relatedCaseId", relatedCase.getId(),
                                    "relatedCaseReference", relatedCase.getCaseReference()))
                        .build());
            });
        }

        // Case activities
        List<CaseActivity> activities = activityRepository.findByComplianceCaseIdOrderByPerformedAtDesc(
                caseId, 
                org.springframework.data.domain.PageRequest.of(0, 100)
        ).getContent();
        
        activities.forEach(activity -> {
            events.add(TimelineEvent.builder()
                    .timestamp(activity.getPerformedAt())
                    .type(activity.getActivityType().name())
                    .description(activity.getDescription())
                    .data(activity)
                    .build());
        });

        // Sort by timestamp
        events.sort(Comparator.comparing(TimelineEvent::getTimestamp));

        return CaseTimelineDTO.builder()
                .caseId(caseId)
                .caseReference(complianceCase.getCaseReference())
                .events(events)
                .build();
    }

    /**
     * Timeline Event DTO
     */
    public static class TimelineEvent {
        private LocalDateTime timestamp;
        private String type;
        private String description;
        private Object data;

        public static TimelineEventBuilder builder() {
            return new TimelineEventBuilder();
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public Object getData() {
            return data;
        }

        public static class TimelineEventBuilder {
            private LocalDateTime timestamp;
            private String type;
            private String description;
            private Object data;

            public TimelineEventBuilder timestamp(LocalDateTime timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public TimelineEventBuilder type(String type) {
                this.type = type;
                return this;
            }

            public TimelineEventBuilder description(String description) {
                this.description = description;
                return this;
            }

            public TimelineEventBuilder data(Object data) {
                this.data = data;
                return this;
            }

            public TimelineEvent build() {
                TimelineEvent event = new TimelineEvent();
                event.timestamp = this.timestamp;
                event.type = this.type;
                event.description = this.description;
                event.data = this.data;
                return event;
            }
        }
    }

    /**
     * Case Timeline DTO
     */
    public static class CaseTimelineDTO {
        private Long caseId;
        private String caseReference;
        private List<TimelineEvent> events;

        public static CaseTimelineDTOBuilder builder() {
            return new CaseTimelineDTOBuilder();
        }

        public Long getCaseId() {
            return caseId;
        }

        public String getCaseReference() {
            return caseReference;
        }

        public List<TimelineEvent> getEvents() {
            return events;
        }

        public static class CaseTimelineDTOBuilder {
            private Long caseId;
            private String caseReference;
            private List<TimelineEvent> events;

            public CaseTimelineDTOBuilder caseId(Long caseId) {
                this.caseId = caseId;
                return this;
            }

            public CaseTimelineDTOBuilder caseReference(String caseReference) {
                this.caseReference = caseReference;
                return this;
            }

            public CaseTimelineDTOBuilder events(List<TimelineEvent> events) {
                this.events = events;
                return this;
            }

            public CaseTimelineDTO build() {
                CaseTimelineDTO dto = new CaseTimelineDTO();
                dto.caseId = this.caseId;
                dto.caseReference = this.caseReference;
                dto.events = this.events;
                return dto;
            }
        }
    }
}

