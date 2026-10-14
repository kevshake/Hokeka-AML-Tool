package com.posgateway.aml.dto.marketing;

import java.time.LocalDateTime;

/**
 * Acknowledgement returned after a marketing-site inquiry is persisted.
 */
public class WebsiteContactResponse {

    private Long inquiryId;
    private String status;
    private LocalDateTime receivedAt;

    public WebsiteContactResponse() {
    }

    public WebsiteContactResponse(Long inquiryId, String status, LocalDateTime receivedAt) {
        this.inquiryId = inquiryId;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public Long getInquiryId() {
        return inquiryId;
    }

    public void setInquiryId(Long inquiryId) {
        this.inquiryId = inquiryId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
