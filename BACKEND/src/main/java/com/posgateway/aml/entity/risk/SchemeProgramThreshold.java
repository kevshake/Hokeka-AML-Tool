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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    public String getSourceDocumentVersion() { return sourceDocumentVersion; }
    public void setSourceDocumentVersion(String sourceDocumentVersion) {
        this.sourceDocumentVersion = sourceDocumentVersion;
    }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(OffsetDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public OffsetDateTime getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(OffsetDateTime effectiveTo) { this.effectiveTo = effectiveTo; }
    public Double getCriticalRate() { return criticalRate; }
    public void setCriticalRate(Double criticalRate) { this.criticalRate = criticalRate; }
    public Long getCriticalVolume() { return criticalVolume; }
    public void setCriticalVolume(Long criticalVolume) { this.criticalVolume = criticalVolume; }
    public Double getHighRate() { return highRate; }
    public void setHighRate(Double highRate) { this.highRate = highRate; }
    public Long getHighVolume() { return highVolume; }
    public void setHighVolume(Long highVolume) { this.highVolume = highVolume; }
    public Double getWarningRate() { return warningRate; }
    public void setWarningRate(Double warningRate) { this.warningRate = warningRate; }
    public Long getWarningVolume() { return warningVolume; }
    public void setWarningVolume(Long warningVolume) { this.warningVolume = warningVolume; }
    public Double getWatchRate() { return watchRate; }
    public void setWatchRate(Double watchRate) { this.watchRate = watchRate; }
    public Long getWatchVolume() { return watchVolume; }
    public void setWatchVolume(Long watchVolume) { this.watchVolume = watchVolume; }
}
