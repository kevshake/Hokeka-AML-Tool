package com.posgateway.aml.entity.limits;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Country Compliance Rules Entity
 * Stores country-specific compliance rules and restrictions
 */
@Entity
@Table(name = "country_compliance_rules", uniqueConstraints = @UniqueConstraint(columnNames = "country_code"))
@Data
public class CountryComplianceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 3, unique = true)
    private String countryCode;

    @Column(name = "country_name", nullable = false, length = 100)
    private String countryName;

    @Column(name = "compliance_requirements", columnDefinition = "TEXT")
    private String complianceRequirements; // JSON

    @Column(name = "transaction_restrictions", columnDefinition = "TEXT")
    private String transactionRestrictions; // JSON

    @Column(name = "required_documentation", columnDefinition = "TEXT")
    private String requiredDocumentation; // JSON

    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Explicit accessors for build environments without Lombok
    public Long getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    public String getComplianceRequirements() {
        return complianceRequirements;
    }

    public void setComplianceRequirements(String complianceRequirements) {
        this.complianceRequirements = complianceRequirements;
    }

    public String getTransactionRestrictions() {
        return transactionRestrictions;
    }

    public void setTransactionRestrictions(String transactionRestrictions) {
        this.transactionRestrictions = transactionRestrictions;
    }

    public String getRequiredDocumentation() {
        return requiredDocumentation;
    }

    public void setRequiredDocumentation(String requiredDocumentation) {
        this.requiredDocumentation = requiredDocumentation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}

