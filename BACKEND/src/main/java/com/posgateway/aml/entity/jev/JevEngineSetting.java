package com.posgateway.aml.entity.jev;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "jev_engine_settings")
public class JevEngineSetting {

    @Id
    @Column(name = "engine_code", length = 64)
    private String engineCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "advisory_only", nullable = false)
    private boolean advisoryOnly = true;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion = "v1";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    public String getEngineCode() {
        return engineCode;
    }

    public void setEngineCode(String engineCode) {
        this.engineCode = engineCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAdvisoryOnly() {
        return advisoryOnly;
    }

    public void setAdvisoryOnly(boolean advisoryOnly) {
        this.advisoryOnly = advisoryOnly;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
