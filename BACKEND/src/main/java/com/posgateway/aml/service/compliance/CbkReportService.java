package com.posgateway.aml.service.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.compliance.CbkSubmissionDto;
import com.posgateway.aml.dto.compliance.CbkSubmitRequest;
import com.posgateway.aml.dto.compliance.CbkSubmitResponse;
import com.posgateway.aml.entity.compliance.CbkSubmission;
import com.posgateway.aml.repository.compliance.CbkSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CBK regulatory submission service (HOK-CBK).
 *
 * <p>Currently the {@link #submitReport submit} call only persists the request
 * locally; the real CBK SOAP/REST integration is a TODO. The persistence is
 * audited via Hibernate Envers ({@code cbk_submissions_aud}).
 */
@Service
public class CbkReportService {

    private static final Logger log = LoggerFactory.getLogger(CbkReportService.class);

    private final CbkSubmissionRepository repository;
    private final ObjectMapper objectMapper;

    public CbkReportService(CbkSubmissionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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
        if (pspId == null) {
            return List.of();
        }

        List<CbkSubmission> rows = (period == null || period.isBlank())
                ? repository.findByPspIdOrderBySubmittedAtDesc(pspId)
                : repository.findByPspIdAndPeriodOrderBySubmittedAtDesc(pspId, period);

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        return rows.stream()
                .filter(r -> fromInstant == null || r.getSubmittedAt() == null
                        || !r.getSubmittedAt().isBefore(fromInstant))
                .filter(r -> toInstant == null || r.getSubmittedAt() == null
                        || r.getSubmittedAt().isBefore(toInstant))
                .map(CbkSubmissionDto::from)
                .toList();
    }

    /**
     * Persist a CBK submission. The reference number is generated as
     * {@code CBK-<year>-<8-char-uuid>} and returned to the caller for display.
     *
     * <p>The actual remote CBK submission is not yet implemented — this method
     * marks the record SUBMITTED locally and returns success. See the TODO inside.
     */
    @Transactional
    public CbkSubmitResponse submitReport(Long pspId, Long userId, CbkSubmitRequest req) {
        if (pspId == null) {
            throw new IllegalArgumentException("PSP context is required to submit a CBK report");
        }
        if (req == null || req.reportId() == null || req.reportId().isBlank()) {
            throw new IllegalArgumentException("reportId is required");
        }
        if (req.period() == null || req.period().isBlank()) {
            throw new IllegalArgumentException("period is required");
        }

        Instant now = Instant.now();
        String reference = "CBK-" + now.atZone(ZoneOffset.UTC).getYear()
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        CbkSubmission entity = new CbkSubmission();
        entity.setPspId(pspId);
        entity.setReportType(req.reportId());
        entity.setPeriod(req.period());
        entity.setPeriodFrom(req.from());
        entity.setPeriodTo(req.to());
        entity.setReferenceNumber(reference);
        entity.setSubmittedAt(now);
        entity.setSubmittedBy(userId);
        entity.setPayloadJson(serializePayload(req));
        entity.setStatus(CbkSubmission.Status.SUBMITTED);

        // TODO(HOK-CBK-API): wire real CBK SOAP/REST submission here.
        // On success: keep status SUBMITTED, store regulatorResponse.
        // On rejection: status REJECTED, errorMessage = remote error.

        CbkSubmission saved = repository.save(entity);
        log.info("CBK submission persisted: pspId={} ref={} reportType={} period={}",
                pspId, saved.getReferenceNumber(), saved.getReportType(), saved.getPeriod());

        return new CbkSubmitResponse(
                saved.getReferenceNumber(),
                "submitted",
                DateTimeFormatter.ISO_INSTANT.format(saved.getSubmittedAt()),
                "CBK report queued for submission"
        );
    }

    private String serializePayload(CbkSubmitRequest req) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reportId", req.reportId());
        payload.put("period", req.period());
        payload.put("from", req.from());
        payload.put("to", req.to());
        if (req.parameters() != null) {
            payload.put("parameters", req.parameters());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize CBK payload, storing minimal record: {}", e.getMessage());
            return "{\"reportId\":\"" + req.reportId() + "\",\"period\":\"" + req.period() + "\"}";
        }
    }
}
