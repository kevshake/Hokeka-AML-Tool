package com.posgateway.aml.service.compliance;

import com.posgateway.aml.dto.compliance.CbkSubmissionDto;
import com.posgateway.aml.dto.compliance.CbkSubmitRequest;
import com.posgateway.aml.dto.compliance.CbkSubmitResponse;
import com.posgateway.aml.entity.compliance.CbkSubmission;
import com.posgateway.aml.repository.compliance.CbkSubmissionRepository;
import com.posgateway.aml.service.cbk.CbkEndpointType;
import com.posgateway.aml.service.cbk.CbkSubmissionOrchestrator;
import com.posgateway.aml.service.cbk.CbkSubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CBK regulatory submission service (HOK-CBK).
 *
 * <p>This service handles the FE-driven on-demand submission path: it persists
 * the request locally for audit and returns a reference to the operator. The
 * actual GDI transmission is performed asynchronously by
 * {@link com.posgateway.aml.service.cbk.CbkSubmissionOrchestrator} using the
 * OAuth2 multipart {@code CbkGdiClient}. Persistence is audited via
 * Hibernate Envers ({@code cbk_submissions_aud}).
 */
@Service
public class CbkReportService {

    private static final Logger log = LoggerFactory.getLogger(CbkReportService.class);

    private final CbkSubmissionRepository repository;
    private final CbkSubmissionOrchestrator submissionOrchestrator;

    public CbkReportService(
            CbkSubmissionRepository repository,
            CbkSubmissionOrchestrator submissionOrchestrator) {
        this.repository = repository;
        this.submissionOrchestrator = submissionOrchestrator;
    }

    /**
     * List CBK submissions for a PSP filtered by period and an optional date window.
     *
     * @param pspId  tenant scope (required)
     * @param period FE period bucket ("daily", "monthly", "2026-Q1", ...). When
     *               blank, returns every submission for the PSP.
     * @param from   optional inclusive lower bound on {@code submittedAt}
     * @param to     optional inclusive upper bound on {@code submittedAt}
     */
    @Transactional(readOnly = true)
    public List<CbkSubmissionDto> listReports(Long pspId, String period, LocalDate from, LocalDate to) {
        List<CbkSubmission> rows;
        boolean cadenceFilter = isCadence(period);
        if (pspId == null) {
            rows = repository.findAll(Sort.by(Sort.Direction.DESC, "submittedAt"));
        } else {
            rows = (period == null || period.isBlank() || cadenceFilter)
                    ? repository.findByPspIdOrderBySubmittedAtDesc(pspId)
                    : repository.findByPspIdAndPeriodOrderBySubmittedAtDesc(pspId, period);
        }

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        return rows.stream()
                .filter(r -> !cadenceFilter || matchesCadence(r, period))
                .filter(r -> fromInstant == null || (r.getSubmittedAt() != null
                        && !r.getSubmittedAt().isBefore(fromInstant)))
                .filter(r -> toInstant == null || (r.getSubmittedAt() != null
                        && r.getSubmittedAt().isBefore(toInstant)))
                .map(CbkSubmissionDto::from)
                .toList();
    }

    private boolean isCadence(String period) {
        return period != null && Set.of("daily", "monthly", "annual")
                .contains(period.trim().toLowerCase(Locale.ROOT));
    }

    private boolean matchesCadence(CbkSubmission row, String requestedCadence) {
        if (row.getReportType() == null) return false;
        try {
            CbkEndpointType endpoint = CbkEndpointType.valueOf(row.getReportType());
            return endpoint.getCadence().name()
                    .equalsIgnoreCase(requestedCadence);
        } catch (IllegalArgumentException legacyRow) {
            return false;
        }
    }

    /**
     * Execute one exact CBK GDI endpoint and return its real transport outcome.
     */
    @Transactional
    public CbkSubmitResponse submitReport(Long pspId, Long userId, CbkSubmitRequest req) {
        if (pspId == null) {
            throw new IllegalArgumentException("PSP context is required to submit a CBK report");
        }
        if (req == null || req.requestedEndpoint() == null
                || req.requestedEndpoint().isBlank()) {
            throw new IllegalArgumentException("endpointType is required");
        }

        CbkEndpointType endpoint;
        try {
            endpoint = CbkEndpointType.valueOf(
                    req.requestedEndpoint().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidEndpoint) {
            throw new IllegalArgumentException(
                    "Unknown CBK endpointType: " + req.requestedEndpoint(), invalidEndpoint);
        }

        CbkSubmissionResult result =
                submissionOrchestrator.runSingleEndpoint(pspId, endpoint);
        Instant completedAt = Instant.now();
        String status = result.isSuccess() ? "submitted" : "failed";
        String message = result.isSuccess()
                ? "CBK accepted the filing"
                : result.getErrorMessage() != null
                        ? result.getErrorMessage()
                        : "CBK filing was not accepted";
        log.info("CBK endpoint execution completed: pspId={} endpoint={} outcome={} http={}",
                pspId, endpoint, result.getOutcome(), result.getHttpStatus());
        return new CbkSubmitResponse(
                result.getReferenceNumber(),
                status,
                DateTimeFormatter.ISO_INSTANT.format(completedAt),
                message
        );
    }
}
