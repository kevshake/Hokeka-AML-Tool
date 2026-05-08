package com.posgateway.aml.entity.reporting;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * SAR (Suspicious Activity Report) narrative template, loaded by
 * regulator + jurisdiction + version. Created by V130.
 *
 * <p>Body uses Mustache-style {@code {{placeholder}}} tokens that are
 * substituted at render time by {@code SarContentGenerationService}.
 * Common placeholders include {@code {{customer_name}}},
 * {@code {{transaction_date}}}, {@code {{case_reference}}},
 * {@code {{filed_by_name}}}, {@code {{total_suspicious_amount}}}.
 */
@Entity
@Table(name = "sar_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uq_sar_template",
                columnNames = {"regulator", "jurisdiction", "version"})
})
public class SarTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FINCEN / FCA / OFAC / CBK / etc. */
    @Column(nullable = false, length = 32)
    private String regulator;

    /** ISO 3166-1 alpha-3 country code (e.g. USA, GBR, KEN). */
    @Column(nullable = false, length = 3)
    private String jurisdiction;

    @Column(nullable = false, length = 16)
    private String version;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public SarTemplate() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegulator() { return regulator; }
    public void setRegulator(String regulator) { this.regulator = regulator; }
    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
