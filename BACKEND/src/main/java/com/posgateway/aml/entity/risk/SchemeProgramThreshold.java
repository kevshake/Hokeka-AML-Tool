package com.posgateway.aml.entity.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "scheme_program_thresholds")
public class SchemeProgramThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "psp_id")
    private Long pspId;

    @Column(name = "program", nullable = false, length = 16)
    private String program;

    @Column(name = "source_document_version", length = 64)
    private String sourceDocumentVersion;

    @Column(name = "effective_from")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Column(name = "critical_rate")
    private Double criticalRate;

    @Column(name = "critical_volume")
    private Long criticalVolume;

    @Column(name = "high_rate")
    private Double highRate;

    @Column(name = "high_volume")
    private Long highVolume;

    @Column(name = "warning_rate")
    private Double warningRate;

    @Column(name = "warning_volume")
    private Long warningVolume;

    @Column(name = "watch_rate")
    private Double watchRate;

    @Column(name = "watch_volume")
    private Long watchVolume;

    /** Returns the persisted row id. */
    public Long getId() { return id; }
    /** Sets the persisted row id. */
    public void setId(Long id) { this.id = id; }
    /** Returns the owning PSP, or null for shared thresholds. */
    public Long getPspId() { return pspId; }
    /** Sets the owning PSP, or null for shared thresholds. */
    public void setPspId(Long pspId) { this.pspId = pspId; }
    /** Returns the scheme program name. */
    public String getProgram() { return program; }
    /** Sets the scheme program name. */
    public void setProgram(String program) { this.program = program; }
    /** Returns the source document version. */
    public String getSourceDocumentVersion() { return sourceDocumentVersion; }
    /** Sets the source document version. */
    public void setSourceDocumentVersion(String sourceDocumentVersion) {
        this.sourceDocumentVersion = sourceDocumentVersion;
    }
    /** Returns the start of the effective period. */
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    /** Sets the start of the effective period. */
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    /** Returns the exclusive end of the effective period. */
    public OffsetDateTime getEffectiveTo() { return effectiveTo; }
    /** Sets the exclusive end of the effective period. */
    public void setEffectiveTo(OffsetDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    /** Returns the critical-stage rate threshold. */
    public Double getCriticalRate() { return criticalRate; }
    /** Sets the critical-stage rate threshold. */
    public void setCriticalRate(Double criticalRate) { this.criticalRate = criticalRate; }
    /** Returns the critical-stage volume threshold. */
    public Long getCriticalVolume() { return criticalVolume; }
    /** Sets the critical-stage volume threshold. */
    public void setCriticalVolume(Long criticalVolume) { this.criticalVolume = criticalVolume; }
    /** Returns the high-stage rate threshold. */
    public Double getHighRate() { return highRate; }
    /** Sets the high-stage rate threshold. */
    public void setHighRate(Double highRate) { this.highRate = highRate; }
    /** Returns the high-stage volume threshold. */
    public Long getHighVolume() { return highVolume; }
    /** Sets the high-stage volume threshold. */
    public void setHighVolume(Long highVolume) { this.highVolume = highVolume; }
    /** Returns the warning-stage rate threshold. */
    public Double getWarningRate() { return warningRate; }
    /** Sets the warning-stage rate threshold. */
    public void setWarningRate(Double warningRate) { this.warningRate = warningRate; }
    /** Returns the warning-stage volume threshold. */
    public Long getWarningVolume() { return warningVolume; }
    /** Sets the warning-stage volume threshold. */
    public void setWarningVolume(Long warningVolume) { this.warningVolume = warningVolume; }
    /** Returns the watch-stage rate threshold. */
    public Double getWatchRate() { return watchRate; }
    /** Sets the watch-stage rate threshold. */
    public void setWatchRate(Double watchRate) { this.watchRate = watchRate; }
    /** Returns the watch-stage volume threshold. */
    public Long getWatchVolume() { return watchVolume; }
    /** Sets the watch-stage volume threshold. */
    public void setWatchVolume(Long watchVolume) { this.watchVolume = watchVolume; }
}
