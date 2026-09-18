package com.posgateway.aml.dto.psp;

import java.time.LocalDateTime;

public class PspReportConfigResponse {
    private Long id;
    private Long pspId;
    private String pspName;
    private String reportUrl;
    private String allowedDomains;
    private String allowedIps;
    private Integer port;
    private Boolean active;
    private LocalDateTime updatedAt;

    public PspReportConfigResponse() {
    }

    public PspReportConfigResponse(Long id, Long pspId, String pspName, String reportUrl, String allowedDomains,
            String allowedIps, Integer port, Boolean active, LocalDateTime updatedAt) {
        this.id = id;
        this.pspId = pspId;
        this.pspName = pspName;
        this.reportUrl = reportUrl;
        this.allowedDomains = allowedDomains;
        this.allowedIps = allowedIps;
        this.port = port;
        this.active = active;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPspId() {
        return pspId;
    }

    public void setPspId(Long pspId) {
        this.pspId = pspId;
    }

    public String getPspName() {
        return pspName;
    }

    public void setPspName(String pspName) {
        this.pspName = pspName;
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

    public static PspReportConfigResponseBuilder builder() {
        return new PspReportConfigResponseBuilder();
    }

    public static class PspReportConfigResponseBuilder {
        private Long id;
        private Long pspId;
        private String pspName;
        private String reportUrl;
        private String allowedDomains;
        private String allowedIps;
        private Integer port;
        private Boolean active;
        private LocalDateTime updatedAt;

        PspReportConfigResponseBuilder() {
        }

        public PspReportConfigResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public PspReportConfigResponseBuilder pspId(Long pspId) {
            this.pspId = pspId;
            return this;
        }

        public PspReportConfigResponseBuilder pspName(String pspName) {
            this.pspName = pspName;
            return this;
        }

        public PspReportConfigResponseBuilder reportUrl(String reportUrl) {
            this.reportUrl = reportUrl;
            return this;
        }

        public PspReportConfigResponseBuilder allowedDomains(String allowedDomains) {
            this.allowedDomains = allowedDomains;
            return this;
        }

        public PspReportConfigResponseBuilder allowedIps(String allowedIps) {
            this.allowedIps = allowedIps;
            return this;
        }

        public PspReportConfigResponseBuilder port(Integer port) {
            this.port = port;
            return this;
        }

        public PspReportConfigResponseBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        public PspReportConfigResponseBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public PspReportConfigResponse build() {
            return new PspReportConfigResponse(id, pspId, pspName, reportUrl, allowedDomains, allowedIps, port, active,
                    updatedAt);
        }

        public String toString() {
            return "PspReportConfigResponse.PspReportConfigResponseBuilder(id=" + this.id + ", pspId=" + this.pspId
                    + ", pspName=" + this.pspName + ", reportUrl=" + this.reportUrl + ", allowedDomains="
                    + this.allowedDomains + ", allowedIps=" + this.allowedIps + ", port=" + this.port + ", active="
                    + this.active + ", updatedAt=" + this.updatedAt + ")";
        }
    }
}
