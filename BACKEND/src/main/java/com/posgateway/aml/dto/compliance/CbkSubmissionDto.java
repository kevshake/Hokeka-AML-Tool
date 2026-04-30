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
        String reportType,
        String period,
        String from,
        String to,
        String submissionStatus,
        String submittedAt,
        String referenceNumber,
        String errorMessage
) {
    public static CbkSubmissionDto from(CbkSubmission s) {
        return new CbkSubmissionDto(
                s.getId() != null ? s.getId().toString() : null,
                s.getReportType(),
                s.getPeriod(),
                s.getPeriodFrom(),
                s.getPeriodTo(),
                toFeStatus(s.getStatus()),
                s.getSubmittedAt() != null
                        ? DateTimeFormatter.ISO_INSTANT.format(s.getSubmittedAt())
                        : null,
                s.getReferenceNumber(),
                s.getErrorMessage()
        );
    }

    private static String toFeStatus(CbkSubmission.Status status) {
        if (status == null) return "pending";
        return switch (status) {
            case SUBMITTED, ACCEPTED -> "submitted";
            case REJECTED -> "failed";
            case DRAFT -> "pending";
        };
    }
}
