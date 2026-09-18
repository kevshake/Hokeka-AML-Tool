package com.posgateway.aml.entity.psp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * PSP Reporting Configuration Entity
 * Stores configuration for sending reports to PSPs (Webhooks, IPs, etc.)
 */
@Entity
@Table(name = "psp_report_configs")
public class PspReportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id", nullable = false, unique = true)
    private Psp psp;

    @Column(name = "report_url", nullable = false)
    private String reportUrl;

    @Column(name = "allowed_domains")
    private String allowedDomains; // Comma-separated domains

    @Column(name = "allowed_ips")
    private String allowedIps; // Comma-separated IPs

    @Column(name = "port")
    private Integer port;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public PspReportConfig() {
    }

    public PspReportConfig(Long id, Psp psp, String reportUrl, String allowedDomains, String allowedIps, Integer port,
            Boolean active, LocalDateTime updatedAt) {
        this.id = id;
        this.psp = psp;
        this.reportUrl = reportUrl;
        this.allowedDomains = allowedDomains;
        this.allowedIps = allowedIps;
        this.port = port;
        this.active = active != null ? active : true;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Psp getPsp() {
        return psp;
    }

    public void setPsp(Psp psp) {
        this.psp = psp;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }

    public String getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(String allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public String getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(String allowedIps) {
        this.allowedIps = allowedIps;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static PspReportConfigBuilder builder() {
        return new PspReportConfigBuilder();
    }

    public static class PspReportConfigBuilder {
        private Long id;
        private Psp psp;
        private String reportUrl;
        private String allowedDomains;
        private String allowedIps;
        private Integer port;
        private Boolean active = true;
        private LocalDateTime updatedAt;

        PspReportConfigBuilder() {
        }

        public PspReportConfigBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public PspReportConfigBuilder psp(Psp psp) {
            this.psp = psp;
            return this;
        }

        public PspReportConfigBuilder reportUrl(String reportUrl) {
            this.reportUrl = reportUrl;
            return this;
        }

        public PspReportConfigBuilder allowedDomains(String allowedDomains) {
            this.allowedDomains = allowedDomains;
            return this;
        }

        public PspReportConfigBuilder allowedIps(String allowedIps) {
            this.allowedIps = allowedIps;
            return this;
        }

        public PspReportConfigBuilder port(Integer port) {
            this.port = port;
            return this;
        }

        public PspReportConfigBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        public PspReportConfigBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public PspReportConfig build() {
            return new PspReportConfig(id, psp, reportUrl, allowedDomains, allowedIps, port, active, updatedAt);
        }

        public String toString() {
            return "PspReportConfig.PspReportConfigBuilder(id=" + this.id + ", psp=" + this.psp + ", reportUrl="
                    + this.reportUrl + ", allowedDomains=" + this.allowedDomains + ", allowedIps=" + this.allowedIps
                    + ", port=" + this.port + ", active=" + this.active + ", updatedAt=" + this.updatedAt + ")";
        }
    }
}
