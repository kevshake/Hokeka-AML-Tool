package com.posgateway.aml.entity.reporting;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * SAR (Suspicious Activity Report) narrative template, loaded by
 * regulator + jurisdiction. Migration is owned by the migration agent.
 *
 * <p>Schema (expected):
 * <pre>
 *   CREATE TABLE sar_templates (
 *       id            bigserial PRIMARY KEY,
 *       regulator     varchar(32) NOT NULL,
 *       jurisdiction  varchar(8)  NOT NULL,
 *       version       int         NOT NULL DEFAULT 1,
 *       body          text        NOT NULL,
 *       active        boolean     NOT NULL DEFAULT true,
 *       created_at    timestamp   NOT NULL DEFAULT now(),
 *       UNIQUE (regulator, jurisdiction, version)
 *   );
 * </pre>
 */
@Entity
@Table(name = "sar_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uq_sar_template",
                columnNames = {"regulator", "jurisdiction", "version"})
})
public class SarTemplate {

    public enum Regulator { FINCEN, NCA, FCA, EU_FIU, CBK, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Regulator regulator;

    @Column(nullable = false, length = 8)
    private String jurisdiction;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SarTemplate() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Regulator getRegulator() { return regulator; }
    public void setRegulator(Regulator regulator) { this.regulator = regulator; }
    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
