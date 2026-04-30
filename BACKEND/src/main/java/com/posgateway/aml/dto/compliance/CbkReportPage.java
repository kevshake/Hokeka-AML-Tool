package com.posgateway.aml.dto.compliance;

import java.util.List;

/**
 * Page-shape consumed by the FE's {@code CbkReportResponse}:
 * <pre>{ content, totalElements, totalPages }</pre>
 *
 * <p>The frontend only renders {@code content}; pagination metadata is
 * provided so the contract matches and future paged backends drop in cleanly.
 */
public record CbkReportPage(
        List<CbkSubmissionDto> content,
        long totalElements,
        int totalPages
) {
    public static CbkReportPage of(List<CbkSubmissionDto> rows) {
        return new CbkReportPage(rows, rows.size(), rows.isEmpty() ? 0 : 1);
    }
}
