package com.posgateway.aml.service.compliance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.compliance.CreateSarRequest;
import com.posgateway.aml.dto.compliance.SarResponse;
import com.posgateway.aml.dto.compliance.UpdateSarRequest;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.mapper.SarMapper;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ComplianceReportingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ComplianceReportingService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * FRC-issued reporting entity identifier for this institution.
     * Set via {@code frc.reporting.entity-id} in application properties.
     */
    @Value("${frc.reporting.entity-id:POSGATEWAY_AML}")
    private String frcEntityId;

    /**
     * Legal name of the reporting institution as registered with the FRC / FinCEN.
     * Set via {@code frc.reporting.entity-name} in application properties.
     */
    @Value("${frc.reporting.entity-name:POS Gateway AML Platform}")
    private String frcEntityName;

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

    /**
     * Generates a goAML-compliant Suspicious Activity Report (SAR) XML document
     * for submission to the FRC (Kenya) or equivalent regulator, using all
     * fields stored on the SAR and its linked compliance case.
     *
     * @param id the SAR database ID; throws {@link IllegalArgumentException} if not found.
     * @return well-formed XML string ready for regulatory submission.
     */
    @SuppressWarnings("deprecation")
    public String generateFincenXml(Long id) {
        SuspiciousActivityReport sar = sarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found: " + id));

        if (!pspIsolationService.isPlatformAdministrator()) {
            Long pspId = pspIsolationService.getCurrentUserPspId();
            if (!pspId.equals(sar.getPspId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied to this SAR");
            }
        }

        log.info("Generating goAML SAR XML for SAR id={} ref={}", sar.getId(), sar.getSarReference());

        String today = LocalDate.now().format(DATE_FMT);
        String filingDate = sar.getFiledAt() != null
                ? sar.getFiledAt().toLocalDate().format(DATE_FMT)
                : today;
        String sarRef = sar.getSarReference() != null ? sar.getSarReference() : "SAR-" + sar.getId();
        String fiuRef = "SAR-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + sar.getId();
        String narrative = sar.getNarrative() != null ? sar.getNarrative() : "";
        String activityType = sar.getSuspiciousActivityType() != null ? sar.getSuspiciousActivityType() : "OTHER";
        String jurisdiction = sar.getJurisdiction() != null ? sar.getJurisdiction() : "KE";
        String sarType = sar.getSarType() != null ? sar.getSarType().name() : "INITIAL";

        // Filing officer details
        String filedByName = "";
        if (sar.getFiledBy() != null) {
            String fn = sar.getFiledBy().getFirstName() != null ? sar.getFiledBy().getFirstName() : "";
            String ln = sar.getFiledBy().getLastName() != null ? sar.getFiledBy().getLastName() : "";
            filedByName = (fn + " " + ln).trim();
        }

        // Linked compliance case
        ComplianceCase linkedCase = sar.getComplianceCase();
        String caseRef = linkedCase != null && linkedCase.getCaseReference() != null
                ? linkedCase.getCaseReference() : "";
        Long merchantId = linkedCase != null ? linkedCase.getMerchantId() : null;
        Long pspId = sar.getPspId();

        // Suspicious transactions
        List<TransactionEntity> txns = sar.getSuspiciousTransactions();
        BigDecimal totalAmount = sar.getTotalSuspiciousAmount() != null
                ? sar.getTotalSuspiciousAmount()
                : BigDecimal.ZERO;
        int txnCount = txns != null ? txns.size() : 0;

        // Determine activity period
        String activityStart = today;
        String activityEnd   = today;
        if (txns != null && !txns.isEmpty()) {
            java.time.LocalDateTime min = null, max = null;
            for (TransactionEntity t : txns) {
                if (t.getTxnTs() != null) {
                    if (min == null || t.getTxnTs().isBefore(min)) min = t.getTxnTs();
                    if (max == null || t.getTxnTs().isAfter(max))  max = t.getTxnTs();
                }
            }
            if (min != null) activityStart = min.toLocalDate().format(DATE_FMT);
            if (max != null) activityEnd   = max.toLocalDate().format(DATE_FMT);
        }

        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<report xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        // Reporting entity header
        appendXml(xml, 1, "<rentity_id>", esc(frcEntityId), "</rentity_id>");
        appendXml(xml, 1, "<rentity_r_name>", esc(frcEntityName), "</rentity_r_name>");
        appendXml(xml, 1, "<submission_code>E</submission_code>");
        appendXml(xml, 1, "<report_code>SAR</report_code>");
        appendXml(xml, 1, "<entity_reference>", esc(sarRef), "</entity_reference>");
        appendXml(xml, 1, "<fiu_ref_number>", esc(fiuRef), "</fiu_ref_number>");
        appendXml(xml, 1, "<submission_date>", today, "</submission_date>");
        appendXml(xml, 1, "<filing_date>", filingDate, "</filing_date>");
        appendXml(xml, 1, "<currency_code_local>KES</currency_code_local>");
        appendXml(xml, 1, "<report_indicators>STRAC</report_indicators>");

        // SAR metadata
        xml.append("  <sar_details>\n");
        appendXml(xml, 2, "<sar_id>", String.valueOf(sar.getId()), "</sar_id>");
        appendXml(xml, 2, "<sar_reference>", esc(sarRef), "</sar_reference>");
        appendXml(xml, 2, "<sar_type>", esc(sarType), "</sar_type>");
        appendXml(xml, 2, "<status>", esc(sar.getStatus().name()), "</status>");
        appendXml(xml, 2, "<jurisdiction>", esc(jurisdiction), "</jurisdiction>");
        appendXml(xml, 2, "<suspicious_activity_type>", esc(activityType), "</suspicious_activity_type>");
        appendXml(xml, 2, "<total_suspicious_amount>", totalAmount.toPlainString(), "</total_suspicious_amount>");
        appendXml(xml, 2, "<transaction_count>", String.valueOf(txnCount), "</transaction_count>");
        appendXml(xml, 2, "<activity_start_date>", activityStart, "</activity_start_date>");
        appendXml(xml, 2, "<activity_end_date>", activityEnd, "</activity_end_date>");
        if (pspId != null) {
            appendXml(xml, 2, "<psp_id>", String.valueOf(pspId), "</psp_id>");
        }
        if (!caseRef.isEmpty()) {
            appendXml(xml, 2, "<case_reference>", esc(caseRef), "</case_reference>");
        }
        if (merchantId != null) {
            appendXml(xml, 2, "<merchant_id>", String.valueOf(merchantId), "</merchant_id>");
        }
        if (sar.getFilingReferenceNumber() != null) {
            appendXml(xml, 2, "<filing_reference_number>", esc(sar.getFilingReferenceNumber()), "</filing_reference_number>");
        }
        if (sar.getFilingDeadline() != null) {
            appendXml(xml, 2, "<filing_deadline>", sar.getFilingDeadline().toLocalDate().format(DATE_FMT), "</filing_deadline>");
        }
        xml.append("  </sar_details>\n");

        // Filing officer
        xml.append("  <filing_officer>\n");
        appendXml(xml, 2, "<name>", esc(filedByName), "</name>");
        if (!filedByName.isEmpty() && sar.getFiledBy() != null) {
            appendXml(xml, 2, "<username>", esc(sar.getFiledBy().getUsername()), "</username>");
        }
        appendXml(xml, 2, "<filing_date>", filingDate, "</filing_date>");
        xml.append("  </filing_officer>\n");

        // Narrative
        xml.append("  <reason>\n");
        appendXml(xml, 2, "<s_indicator>STRAC</s_indicator>");
        appendXml(xml, 2, "<description>", esc(activityType), "</description>");
        appendXml(xml, 2, "<narrative>", esc(narrative), "</narrative>");
        xml.append("  </reason>\n");

        // Transaction blocks
        if (txns != null && !txns.isEmpty()) {
            for (TransactionEntity tx : txns) {
                String txDate = tx.getTxnTs() != null
                        ? tx.getTxnTs().toLocalDate().format(DATE_FMT)
                        : today;
                String txAmount = tx.getAmountCents() != null
                        ? BigDecimal.valueOf(tx.getAmountCents()).divide(BigDecimal.valueOf(100)).toPlainString()
                        : "0.00";
                xml.append("  <transaction>\n");
                appendXml(xml, 2, "<tran_date>", txDate, "</tran_date>");
                appendXml(xml, 2, "<tran_id>", esc(tx.getTxnId() != null ? String.valueOf(tx.getTxnId()) : ""), "</tran_id>");
                appendXml(xml, 2, "<tran_amount_local>", txAmount, "</tran_amount_local>");
                appendXml(xml, 2, "<tran_amount_original>", txAmount, "</tran_amount_original>");
                appendXml(xml, 2, "<mode_of_payment>E</mode_of_payment>");
                appendXml(xml, 2, "<from_funds_code>Z</from_funds_code>");
                appendXml(xml, 2, "<to_funds_code>Z</to_funds_code>");
                xml.append("    <t_from_my_client>\n");
                appendXml(xml, 3, "<from_funds_code>Z</from_funds_code>");
                appendXml(xml, 3, "<from_country>", esc(jurisdiction.length() == 2 ? jurisdiction : "KE"), "</from_country>");
                xml.append("    </t_from_my_client>\n");
                xml.append("    <t_to_my_client>\n");
                appendXml(xml, 3, "<to_funds_code>Z</to_funds_code>");
                appendXml(xml, 3, "<to_country>", esc(jurisdiction.length() == 2 ? jurisdiction : "KE"), "</to_country>");
                if (merchantId != null) {
                    appendXml(xml, 3, "<to_entity_id>", String.valueOf(merchantId), "</to_entity_id>");
                }
                xml.append("    </t_to_my_client>\n");
                xml.append("  </transaction>\n");
            }
        } else {
            // Minimal schema-valid transaction block when no transactions linked
            xml.append("  <transaction>\n");
            appendXml(xml, 2, "<tran_date>", today, "</tran_date>");
            appendXml(xml, 2, "<tran_description>", esc(activityType), "</tran_description>");
            appendXml(xml, 2, "<tran_amount_local>0.00</tran_amount_local>");
            appendXml(xml, 2, "<tran_amount_original>0.00</tran_amount_original>");
            appendXml(xml, 2, "<mode_of_payment>E</mode_of_payment>");
            appendXml(xml, 2, "<from_funds_code>Z</from_funds_code>");
            appendXml(xml, 2, "<to_funds_code>Z</to_funds_code>");
            xml.append("    <t_from_my_client>\n");
            appendXml(xml, 3, "<from_funds_code>Z</from_funds_code>");
            appendXml(xml, 3, "<from_country>KE</from_country>");
            xml.append("    </t_from_my_client>\n");
            xml.append("    <t_to_my_client>\n");
            appendXml(xml, 3, "<to_funds_code>Z</to_funds_code>");
            appendXml(xml, 3, "<to_country>KE</to_country>");
            xml.append("    </t_to_my_client>\n");
            xml.append("  </transaction>\n");
        }

        xml.append("</report>");

        String result = xml.toString();
        log.info("SAR XML generated ({} chars) for SAR id={} ref={}", result.length(), sar.getId(), sarRef);
        return result;
    }

    // -------------------------------------------------------------------------
    // Private XML helpers
    // -------------------------------------------------------------------------

    private static void appendXml(StringBuilder sb, int indent, String... parts) {
        sb.append("  ".repeat(indent));
        for (String part : parts) {
            sb.append(part);
        }
        sb.append('\n');
    }

    @SuppressWarnings("deprecation")
    private static String esc(String value) {
        return StringEscapeUtils.escapeXml11(value != null ? value : "");
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

