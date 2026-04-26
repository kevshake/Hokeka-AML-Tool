package com.posgateway.aml.service.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.compliance.CreateSarRequest;
import com.posgateway.aml.dto.compliance.SarResponse;
import com.posgateway.aml.dto.compliance.UpdateSarRequest;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.mapper.SarMapper;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ComplianceReportingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ComplianceReportingService.class);

    private final SuspiciousActivityReportRepository sarRepository;
    private final ObjectMapper objectMapper;
    private final SarMapper sarMapper;
    private final PspIsolationService pspIsolationService;

    public ComplianceReportingService(SuspiciousActivityReportRepository sarRepository,
            ObjectMapper objectMapper,
            SarMapper sarMapper,
            PspIsolationService pspIsolationService) {
        this.sarRepository = sarRepository;
        this.objectMapper = objectMapper;
        this.sarMapper = sarMapper;
        this.pspIsolationService = pspIsolationService;
    }

    @Transactional(readOnly = true)
    public java.util.List<SarResponse> getAllSars(String status) {
        // HOK-61: use PspIsolationService for reliable PSP resolution.
        // pspId == 0 means platform admin (sees all); pspId > 0 means PSP user (scoped).
        Long pspId = pspIsolationService.getCurrentUserPspId();
        boolean isPlatformAdmin = pspIsolationService.isPlatformAdministrator();

        java.util.List<SuspiciousActivityReport> sars;
        if (status != null && !status.isEmpty()) {
            try {
                SarStatus sarStatus = SarStatus.valueOf(status);
                sars = isPlatformAdmin
                    ? sarRepository.findByStatus(sarStatus)
                    : sarRepository.findByPspIdAndStatus(pspId, sarStatus);
            } catch (IllegalArgumentException e) {
                sars = java.util.List.of();
            }
        } else {
            sars = isPlatformAdmin
                ? sarRepository.findAll()
                : sarRepository.findByPspId(pspId);
        }
        return sars.stream()
                .map(sarMapper::toResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public SarResponse createSar(CreateSarRequest request) {
        log.info("Creating new SAR (caseId={} merchantId={})", request.getCaseId(), request.getMerchantId());

        Long pspId = pspIsolationService.getCurrentUserPspId();


        SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                .sarReference("SAR-" + UUID.randomUUID())
                .status(SarStatus.DRAFT)
                .narrative(request.getNarrative())
                .suspiciousActivityType("GENERAL")
                .jurisdiction("UNKNOWN")
                .pspId(pspId) // Link to PSP
                .build();

        sar = sarRepository.save(sar);
        return sarMapper.toResponse(sar);
    }

    @Transactional
    public SarResponse updateSar(Long id, UpdateSarRequest request) {
        SuspiciousActivityReport sar = sarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + id));

        if (!pspIsolationService.isPlatformAdministrator()) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (!pspId.equals(sar.getPspId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied to this SAR");
            }
        }

        if (request.getStatus() != null) {
            sar.setStatus(SarStatus.valueOf(request.getStatus()));
        }
        if (request.getNarrative() != null) {
            sar.setNarrative(request.getNarrative());
        }

        sar = sarRepository.save(sar);
        return sarMapper.toResponse(sar);
    }

    @Transactional
    public SarResponse fileSar(Long id) {
        SuspiciousActivityReport sar = sarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + id));

        if (!pspIsolationService.isPlatformAdministrator()) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (!pspId.equals(sar.getPspId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied to this SAR");
            }
        }

        if (sar.getStatus() == SarStatus.FILED) {
            throw new IllegalStateException("SAR is already filed");
        }

        sar.setStatus(SarStatus.FILED);
        sar.setFiledAt(LocalDateTime.now());

        // Simulating submission to FinCEN
        log.info("Filing SAR {} to FinCEN...", id);

        return sarMapper.toResponse(sarRepository.save(sar));
    }

    public String generateFincenXml(Long id) {
        SuspiciousActivityReport sar = sarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + id));

        if (!pspIsolationService.isPlatformAdministrator()) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (!pspId.equals(sar.getPspId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied to this SAR");
            }
        }

        // Simplified mock XML generation
        return String.format(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<SAR>\n" +
                        "  <ActivityReportID>%d</ActivityReportID>\n" +
                        "  <FilingDate>%s</FilingDate>\n" +
                        "  <Narrative><![CDATA[%s]]></Narrative>\n" +
                        "  <SignerIdentifier>%s</SignerIdentifier>\n" +
                        "</SAR>",
                sar.getId(),
                sar.getFiledAt() != null ? sar.getFiledAt().toString() : "",
                sar.getNarrative() != null ? sar.getNarrative() : "",
                UUID.randomUUID().toString());
    }

    @SuppressWarnings("unused")
    private String toJson(Map<String, Object> map) {
        if (map == null)
            return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Error serializing JSON", e);
            return "{}";
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> fromJson(String json) {
        if (json == null)
            return null;
        try {
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (JsonProcessingException e) {
            log.error("Error deserializing JSON", e);
            return null;
        }
    }

}

