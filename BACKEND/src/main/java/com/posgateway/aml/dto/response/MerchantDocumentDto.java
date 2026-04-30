package com.posgateway.aml.dto.response;

import com.posgateway.aml.entity.merchant.MerchantDocument;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for MerchantDocument that exposes a public {@code fileUrl}
 * (a streaming endpoint) instead of the server-local {@code filePath}.
 */
public record MerchantDocumentDto(
        Long id,
        Long merchantId,
        String documentType,
        String fileName,
        String status,
        String fileUrl,
        LocalDate expiryDate,
        LocalDateTime uploadedAt,
        LocalDateTime verifiedAt,
        Integer version,
        Boolean isCurrentVersion
) {
    public static MerchantDocumentDto from(MerchantDocument doc) {
        if (doc == null) {
            return null;
        }
        // Public, app-relative URL — context path /api/v1 is added by the
        // browser when invoked via the existing apiClient base.
        String fileUrl = "/api/v1/documents/" + doc.getDocumentId() + "/file";
        return new MerchantDocumentDto(
                doc.getDocumentId(),
                doc.getMerchantId(),
                doc.getDocumentType(),
                doc.getFileName(),
                doc.getStatus(),
                fileUrl,
                doc.getExpiryDate(),
                doc.getUploadedAt(),
                doc.getVerifiedAt(),
                doc.getVersion(),
                doc.getIsCurrentVersion()
        );
    }
}
