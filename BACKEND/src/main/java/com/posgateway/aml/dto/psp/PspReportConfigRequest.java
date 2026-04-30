package com.posgateway.aml.dto.psp;

public class PspReportConfigRequest {
    private String reportUrl;
    private String allowedDomains;
    private String allowedIps;
    private Integer port;
    private Boolean active;

    public PspReportConfigRequest() {
    }

    public PspReportConfigRequest(String reportUrl, String allowedDomains, String allowedIps, Integer port,
            Boolean active) {
        this.reportUrl = reportUrl;
        this.allowedDomains = allowedDomains;
        this.allowedIps = allowedIps;
        this.port = port;
        this.active = active;
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

    public static PspReportConfigRequestBuilder builder() {
        return new PspReportConfigRequestBuilder();
    }

    public static class PspReportConfigRequestBuilder {
        private String reportUrl;
        private String allowedDomains;
        private String allowedIps;
        private Integer port;
        private Boolean active;

        PspReportConfigRequestBuilder() {
        }

        public PspReportConfigRequestBuilder reportUrl(String reportUrl) {
            this.reportUrl = reportUrl;
            return this;
        }

        public PspReportConfigRequestBuilder allowedDomains(String allowedDomains) {
            this.allowedDomains = allowedDomains;
            return this;
        }

        public PspReportConfigRequestBuilder allowedIps(String allowedIps) {
            this.allowedIps = allowedIps;
            return this;
        }

        public PspReportConfigRequestBuilder port(Integer port) {
            this.port = port;
            return this;
        }

        public PspReportConfigRequestBuilder active(Boolean active) {
            this.active = active;
            return this;
        }

        public PspReportConfigRequest build() {
            return new PspReportConfigRequest(reportUrl, allowedDomains, allowedIps, port, active);
        }

        public String toString() {
            return "PspReportConfigRequest.PspReportConfigRequestBuilder(reportUrl=" + this.reportUrl
                    + ", allowedDomains=" + this.allowedDomains + ", allowedIps=" + this.allowedIps + ", port="
                    + this.port + ", active=" + this.active + ")";
        }
    }
}
