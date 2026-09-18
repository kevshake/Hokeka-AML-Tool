package com.posgateway.aml.dto.compliance;

import com.posgateway.aml.entity.compliance.CbkSubmission;

import java.time.format.DateTimeFormatter;

/**
 * Row shape consumed by the CBK Submission Panel
 * ({@code FRONTEND/src/features/api/cbkReportQueries.ts -> CbkReportRow}).
 *
 * <p>Field names are deliberately FE-aligned ({@code submissionStatus}, lowercase
 * status string, {@code from}/{@code to} mapped to entity {@code periodFrom}/{@code periodTo}).
 */
public record CbkSubmissionDto(
        String id,
        Long pspId,
        String reportType,
        String endpointType,
        String period,
        String from,
        String to,
        String submissionStatus,
        String status,
        String submittedAt,
        String attemptedAt,
        String referenceNumber,
        String requestId,
        Integer recordCount,
        String errorMessage
) {
    public static CbkSubmissionDto from(CbkSubmission s) {
        String timestamp = s.getSubmittedAt() != null
                ? DateTimeFormatter.ISO_INSTANT.format(s.getSubmittedAt())
                : null;
        return new CbkSubmissionDto(
                s.getId() != null ? s.getId().toString() : null,
                s.getPspId(),
                s.getReportType(),
                s.getReportType(),
                s.getPeriod(),
                s.getPeriodFrom(),
                s.getPeriodTo(),
                toFeStatus(s.getStatus()),
                toOperationalStatus(s.getStatus()),
                timestamp,
                timestamp,
                s.getReferenceNumber(),
                s.getReferenceNumber(),
                s.getSourceRecordCount(),
                s.getErrorMessage()
        );
    }

    private static String toFeStatus(CbkSubmission.Status status) {
        if (status == null) return "pending";
        return switch (status) {
            case ACCEPTED -> "submitted";
            case REJECTED -> "failed";
            case DRAFT, SUBMITTED -> "pending";
        };
    }

    private static String toOperationalStatus(CbkSubmission.Status status) {
        if (status == null) return "PENDING";
        return switch (status) {
            case ACCEPTED -> "SUCCESS";
            case REJECTED -> "FAILED";
            case DRAFT, SUBMITTED -> "PENDING";
        };
    }
}
