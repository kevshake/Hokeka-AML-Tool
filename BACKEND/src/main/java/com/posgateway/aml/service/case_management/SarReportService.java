package com.posgateway.aml.service.case_management;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.CaseAlert;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringEscapeUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating Suspicious Activity Reports (SAR) and Suspicious
 * Transaction Reports (STR).
 * Supports:
 * 1. XML generation for CBK (Central Bank of Kenya) compliance.
 * 2. JSON generation for internal/generic reporting.
 */
@Service
public class SarReportService {

    private static final Logger logger = LoggerFactory.getLogger(SarReportService.class);
    private static final DateTimeFormatter CBK_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ComplianceCaseRepository complianceCaseRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public SarReportService(ComplianceCaseRepository complianceCaseRepository, ObjectMapper objectMapper) {
        this.complianceCaseRepository = complianceCaseRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate STR XML payload for CBK submission.
     */
    public String generateStrXml(Long caseId) {
        ComplianceCase cCase = getCase(caseId);
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<STR_Submission>\n");
        xml.append("  <Header>\n");
        xml.append("    <ReportType>STR</ReportType>\n");
        xml.append("    <SubmissionDate>").append(java.time.LocalDateTime.now().format(CBK_DATE_FMT))
                .append("</SubmissionDate>\n");
        xml.append("    <ReportingEntity>POS_GATEWAY_AML</ReportingEntity>\n");
        xml.append("  </Header>\n");

        xml.append("  <CaseDetails>\n");
        xml.append("    <CaseReference>").append(escape(cCase.getCaseReference())).append("</CaseReference>\n");
        xml.append("    <MerchantId>").append(cCase.getMerchantId()).append("</MerchantId>\n");
        xml.append("    <Priority>").append(cCase.getPriority()).append("</Priority>\n");
        xml.append("    <ResolutionNotes>").append(escape(cCase.getResolutionNotes())).append("</ResolutionNotes>\n");
        xml.append("  </CaseDetails>\n");

        xml.append("  <SuspiciousActivity>\n");
        if (cCase.getAlerts() != null) {
            for (CaseAlert alert : cCase.getAlerts()) {
                xml.append("    <Alert>\n");
                xml.append("      <Type>").append(escape(alert.getAlertType())).append("</Type>\n");
                xml.append("      <Description>").append(escape(alert.getDescription())).append("</Description>\n");
                xml.append("      <TriggeredAt>").append(alert.getTriggeredAt().format(CBK_DATE_FMT))
                        .append("</TriggeredAt>\n");
                xml.append("    </Alert>\n");
            }
        }
        xml.append("  </SuspiciousActivity>\n");
        xml.append("</STR_Submission>");

        return xml.toString();
    }

    /**
     * Generate generic SAR JSON payload.
     */
    public String generateSarJson(Long caseId) {
        try {
            ComplianceCase cCase = getCase(caseId);
            Map<String, Object> report = new HashMap<>();

            report.put("report_type", "SAR");
            report.put("generated_at", java.time.LocalDateTime.now().toString());
            report.put("case_reference", cCase.getCaseReference());
            report.put("merchant_id", cCase.getMerchantId());
            report.put("status", cCase.getStatus());
            report.put("resolution_notes", cCase.getResolutionNotes());

            // Map alerts
            if (cCase.getAlerts() != null) {
                report.put("alerts", cCase.getAlerts());
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        } catch (Exception e) {
            logger.error("Error generating SAR JSON for case {}", caseId, e);
            throw new RuntimeException("Failed to generate SAR JSON", e);
        }
    }

    private ComplianceCase getCase(Long caseId) {
        return complianceCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
    }

    private String escape(String input) {
        return StringEscapeUtils.escapeXml11(input != null ? input : "");
    }
}
