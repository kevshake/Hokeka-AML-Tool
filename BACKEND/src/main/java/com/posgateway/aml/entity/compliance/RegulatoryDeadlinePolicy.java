package com.posgateway.aml.entity.compliance;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "regulatory_deadline_policies")
public class RegulatoryDeadlinePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_code", nullable = false, unique = true, length = 80)
    private String policyCode;

    @Column(nullable = false, length = 8)
    private String jurisdiction;

    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType;

    @Column(name = "deadline_amount", nullable = false)
    private int deadlineAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_unit", nullable = false, length = 24)
    private DeadlineUnit deadlineUnit;

    @Column(name = "warning_hours", nullable = false)
    private int warningHours;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "legal_reference", nullable = false, columnDefinition = "TEXT")
    private String legalReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * W18-1 fix: createdAt is NOT NULL but nothing stamped it -- any programmatic
     * save (as opposed to the Flyway-seeded rows this entity currently only ever gets)
     * would have thrown a NOT NULL constraint violation at insert time. Same
     * @PrePersist pattern already used by the sibling compliance entities (CaseNote,
     * CaseEvidence, CaseActivity).
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getPolicyCode() { return policyCode; }
    public String getJurisdiction() { return jurisdiction; }
    public String getReportType() { return reportType; }
    public int getDeadlineAmount() { return deadlineAmount; }
    public DeadlineUnit getDeadlineUnit() { return deadlineUnit; }
    public int getWarningHours() { return warningHours; }
    public Long getPspId() { return pspId; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public boolean isActive() { return active; }
    public String getLegalReference() { return legalReference; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
