package com.posgateway.aml.entity.rules;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Table(name = "rule_definitions")
@Data
@Audited
public class RuleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(columnDefinition = "TEXT")
    private String ruleJson; // JSON structure of the rule conditions/actions

    @Column(columnDefinition = "TEXT")
    private String drlContent; // Generated DRL string

    @Column(name = "rule_type")
    private String ruleType; // SPEL, DROOLS_DRL, JAVA_BEAN

    @Column(columnDefinition = "TEXT", name = "rule_expression")
    private String ruleExpression; // The actual expression (SpEL or raw DRL user input)

    @Column(name = "score_impact")
    private Integer score; // Score impact if triggered (e.g., +50)

    @Column(name = "action_type")
    private String action; // BLOCK, HOLD, ALERT, ALLOW

    private Integer priority;

    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "psp_id")
    private Long pspId; // Null for super admin rules, set for PSP-specific rules

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** TRUE for the curated default catalog seeded in V135. Cannot be deleted. */
    @Column(name = "is_system_managed", nullable = false)
    private boolean systemManaged = false;

    @Column(name = "category", length = 20)
    private String category; // AML | FRAUD | SCREENING

    @Column(name = "rule_subtype", length = 64)
    private String ruleSubtype; // Velocity, Anomaly detection, Pattern recognition, Blacklist, Screening, ...

    @Column(name = "applies_to", length = 20)
    private String appliesTo; // Transaction | User

    @Column(name = "typology")
    private String typology; // Money mules, Structuring, Unusual behaviour, ...

    @Column(name = "checks_for", columnDefinition = "TEXT")
    private String checksFor;

    @Column(name = "external_code", length = 20)
    private String externalCode; // R-1, R-2, ...

    @Column(name = "recommended", nullable = false)
    private boolean recommended = false;

    @Column(name = "sample_use_case", columnDefinition = "TEXT")
    private String sampleUseCase;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters; // JSON string — editor renders dropdown form from this schema

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Manual Getters and Setters to ensure availability
    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getRuleExpression() {
        return ruleExpression;
    }

    public void setRuleExpression(String ruleExpression) {
        this.ruleExpression = ruleExpression;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRuleJson() {
        return ruleJson;
    }

    public void setRuleJson(String ruleJson) {
        this.ruleJson = ruleJson;
    }

    public String getDrlContent() {
        return drlContent;
    }

    public void setDrlContent(String drlContent) {
        this.drlContent = drlContent;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
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

    public boolean isSystemManaged() { return systemManaged; }
    public void setSystemManaged(boolean systemManaged) { this.systemManaged = systemManaged; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRuleSubtype() { return ruleSubtype; }
    public void setRuleSubtype(String ruleSubtype) { this.ruleSubtype = ruleSubtype; }

    public String getAppliesTo() { return appliesTo; }
    public void setAppliesTo(String appliesTo) { this.appliesTo = appliesTo; }

    public String getTypology() { return typology; }
    public void setTypology(String typology) { this.typology = typology; }

    public String getChecksFor() { return checksFor; }
    public void setChecksFor(String checksFor) { this.checksFor = checksFor; }

    public String getExternalCode() { return externalCode; }
    public void setExternalCode(String externalCode) { this.externalCode = externalCode; }

    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }

    public String getSampleUseCase() { return sampleUseCase; }
    public void setSampleUseCase(String sampleUseCase) { this.sampleUseCase = sampleUseCase; }

    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
}
