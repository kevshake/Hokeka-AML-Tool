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
        String downloadUrl,
        String contentType,
        Long fileSize,
        String sha256Hash,
        LocalDate expiryDate,
        LocalDateTime uploadedAt,
        LocalDateTime verifiedAt,
        String verifiedBy,
        String verificationNotes,
        String verificationMethod,
        String malwareScanStatus,
        String malwareScanEngine,
        String malwareThreatName,
        LocalDateTime malwareScannedAt,
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
                "/api/v1/documents/" + doc.getDocumentId() + "/download",
                doc.getContentType(),
                doc.getFileSize(),
                doc.getSha256Hash(),
                doc.getExpiryDate(),
                doc.getUploadedAt(),
                doc.getVerifiedAt(),
                doc.getVerifiedBy(),
                doc.getVerificationNotes(),
                doc.getVerificationMethod(),
                doc.getMalwareScanStatus(),
                doc.getMalwareScanEngine(),
                doc.getMalwareThreatName(),
                doc.getMalwareScannedAt(),
                doc.getVersion(),
                doc.getIsCurrentVersion()
        );
    }
}
