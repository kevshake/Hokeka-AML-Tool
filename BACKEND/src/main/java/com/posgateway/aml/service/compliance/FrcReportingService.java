package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.compliance.CaseAlert;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.apache.commons.lang3.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * FRC (Financial Reporting Centre — Kenya) goAML report generator.
 *
 * Produces XML payloads that conform to the goAML 4.x submission schema
 * used by the FRC for STR, CTR, and ANNREP submissions.
 *
 * Spec references:
 *   goAML XML Schema v4 (FIU Kenya / FATF)
 *   CBK AML/CFT Regulations 2023, Regulation 18 (electronic filing)
 */
@Service
public class FrcReportingService {

    private static final Logger log = LoggerFactory.getLogger(FrcReportingService.class);

    /** ISO-8601 date used throughout goAML date fields. */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * FRC-issued reporting entity identifier for this institution.
     * Set via {@code frc.reporting.entity-id} in application properties.
     * Defaults to {@code POSGATEWAY_AML} when not configured.
     */
    @Value("${frc.reporting.entity-id:POSGATEWAY_AML}")
    private String frcEntityId;

    /**
     * Legal / registered name of the reporting institution as registered with the FRC.
     * Set via {@code frc.reporting.entity-name} in application properties.
     */
    @Value("${frc.reporting.entity-name:POS Gateway AML Platform}")
    private String frcEntityName;

    private final ComplianceCaseRepository complianceCaseRepository;

    public FrcReportingService(ComplianceCaseRepository complianceCaseRepository) {
        this.complianceCaseRepository = complianceCaseRepository;
    }

    // =========================================================================
    // STR — Suspicious Transaction Report
    // =========================================================================

    /**
     * Generates a goAML-compliant Suspicious Transaction Report (STR) XML
     * document for the given compliance case.
     *
     * @param complianceCase the resolved compliance case to report; must not be null.
     * @return well-formed XML string ready for FRC submission.
     */
    public String generateSuspiciousTransactionReport(ComplianceCase complianceCase) {
        log.info("Generating goAML STR for case id={} ref={}",
                complianceCase.getId(), complianceCase.getCaseReference());

        String today = LocalDate.now().format(DATE_FMT);
        String fiuRef = buildFiuRef("STR", complianceCase.getId());
        String entityRef = safe(complianceCase.getCaseReference(), "CASE-" + complianceCase.getId());
        String description = safe(complianceCase.getDescription(), "Suspicious activity identified during AML screening");

        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<report xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        // --- Reporting entity header ---
        appendLine(xml, 1, "<rentity_id>", esc(frcEntityId), "</rentity_id>");
        appendLine(xml, 1, "<rentity_r_name>", esc(frcEntityName), "</rentity_r_name>");
        appendLine(xml, 1, "<submission_code>E</submission_code>");
        appendLine(xml, 1, "<report_code>STR</report_code>");
        appendLine(xml, 1, "<entity_reference>", esc(entityRef), "</entity_reference>");
        appendLine(xml, 1, "<fiu_ref_number>", esc(fiuRef), "</fiu_ref_number>");
        appendLine(xml, 1, "<submission_date>", today, "</submission_date>");
        appendLine(xml, 1, "<currency_code_local>KES</currency_code_local>");
        appendLine(xml, 1, "<report_indicators>STRAC</report_indicators>");

        // --- Case narrative ---
        xml.append("  <reason>\n");
        appendLine(xml, 2, "<s_indicator>STRAC</s_indicator>");
        appendLine(xml, 2, "<description>", esc(description), "</description>");
        xml.append("  </reason>\n");

        // --- Alert blocks (suspicious activity indicators) ---
        List<CaseAlert> alerts = complianceCase.getAlerts();
        if (alerts != null && !alerts.isEmpty()) {
            for (CaseAlert alert : alerts) {
                xml.append("  <transaction>\n");
                appendLine(xml, 2, "<tran_date>", formatDate(alert.getTriggeredAt()), "</tran_date>");
                appendLine(xml, 2, "<tran_description>", esc(safe(alert.getDescription(), alert.getAlertType())), "</tran_description>");
                appendLine(xml, 2, "<tran_amount_local>0.00</tran_amount_local>");
                appendLine(xml, 2, "<tran_amount_original>0.00</tran_amount_original>");
                appendLine(xml, 2, "<mode_of_payment>E</mode_of_payment>");
                appendLine(xml, 2, "<from_funds_code>Z</from_funds_code>");
                appendLine(xml, 2, "<to_funds_code>Z</to_funds_code>");

                xml.append("    <t_from_my_client>\n");
                appendLine(xml, 3, "<from_funds_code>Z</from_funds_code>");
                appendLine(xml, 3, "<from_country>KE</from_country>");
                if (complianceCase.getMerchantId() != null) {
                    appendLine(xml, 3, "<from_entity_id>", esc(String.valueOf(complianceCase.getMerchantId())), "</from_entity_id>");
                }
                xml.append("    </t_from_my_client>\n");

                xml.append("    <t_to_my_client>\n");
                appendLine(xml, 3, "<to_funds_code>Z</to_funds_code>");
                appendLine(xml, 3, "<to_country>KE</to_country>");
                xml.append("    </t_to_my_client>\n");

                xml.append("  </transaction>\n");
            }
        } else {
            // Emit a minimal transaction block so the document remains schema-valid
            xml.append("  <transaction>\n");
            appendLine(xml, 2, "<tran_date>", today, "</tran_date>");
            appendLine(xml, 2, "<tran_description>", esc(description), "</tran_description>");
            appendLine(xml, 2, "<tran_amount_local>0.00</tran_amount_local>");
            appendLine(xml, 2, "<tran_amount_original>0.00</tran_amount_original>");
            appendLine(xml, 2, "<mode_of_payment>E</mode_of_payment>");
            appendLine(xml, 2, "<from_funds_code>Z</from_funds_code>");
            appendLine(xml, 2, "<to_funds_code>Z</to_funds_code>");
            xml.append("    <t_from_my_client>\n");
            appendLine(xml, 3, "<from_funds_code>Z</from_funds_code>");
            appendLine(xml, 3, "<from_country>KE</from_country>");
            xml.append("    </t_from_my_client>\n");
            xml.append("    <t_to_my_client>\n");
            appendLine(xml, 3, "<to_funds_code>Z</to_funds_code>");
            appendLine(xml, 3, "<to_country>KE</to_country>");
            xml.append("    </t_to_my_client>\n");
            xml.append("  </transaction>\n");
        }

        xml.append("</report>");

        String result = xml.toString();
        log.debug("STR generated ({} chars) for case id={}", result.length(), complianceCase.getId());
        return result;
    }

    // =========================================================================
    // CTR — Cash Transaction Report
    // =========================================================================

    /**
     * Generates a goAML-compliant Cash Transaction Report (CTR) XML document
     * for a single large-value transaction.
     *
     * @param transaction the cash transaction to report; must not be null.
     * @return well-formed XML string ready for FRC submission.
     */
    public String generateCashTransactionReport(Transaction transaction) {
        log.info("Generating goAML CTR for transaction id={}", transaction.getTransactionId());

        String today = LocalDate.now().format(DATE_FMT);
        String tranDate = transaction.getTransactionTimestamp() != null
                ? transaction.getTransactionTimestamp().toLocalDate().format(DATE_FMT)
                : today;
        String fiuRef = buildFiuRef("CTR", null);
        String currencyCode = safe(transaction.getCurrencyCode(), "KES");
        String amount = transaction.getAmount() != null
                ? transaction.getAmount().toPlainString()
                : "0.00";
        String description = buildCtrDescription(transaction);
        String modeOfPayment = deriveModeOfPayment(transaction.getTransactionType());

        StringBuilder xml = new StringBuilder(1536);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<report xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        appendLine(xml, 1, "<rentity_id>", esc(frcEntityId), "</rentity_id>");
        appendLine(xml, 1, "<rentity_r_name>", esc(frcEntityName), "</rentity_r_name>");
        appendLine(xml, 1, "<submission_code>E</submission_code>");
        appendLine(xml, 1, "<report_code>CTR</report_code>");
        appendLine(xml, 1, "<entity_reference>", esc(transaction.getTransactionId()), "</entity_reference>");
        appendLine(xml, 1, "<fiu_ref_number>", esc(fiuRef), "</fiu_ref_number>");
        appendLine(xml, 1, "<submission_date>", today, "</submission_date>");
        appendLine(xml, 1, "<currency_code_local>KES</currency_code_local>");

        xml.append("  <transaction>\n");
        appendLine(xml, 2, "<tran_date>", tranDate, "</tran_date>");
        appendLine(xml, 2, "<tran_description>", esc(description), "</tran_description>");
        appendLine(xml, 2, "<tran_amount_local>", amount, "</tran_amount_local>");
        appendLine(xml, 2, "<tran_amount_original>", amount, "</tran_amount_original>");
        appendLine(xml, 2, "<tran_currency_original>", esc(currencyCode), "</tran_currency_original>");
        appendLine(xml, 2, "<mode_of_payment>", modeOfPayment, "</mode_of_payment>");
        appendLine(xml, 2, "<from_funds_code>C</from_funds_code>");
        appendLine(xml, 2, "<to_funds_code>C</to_funds_code>");

        xml.append("    <t_from_my_client>\n");
        appendLine(xml, 3, "<from_funds_code>C</from_funds_code>");
        appendLine(xml, 3, "<from_country>KE</from_country>");
        if (transaction.getAccountNumber() != null) {
            appendLine(xml, 3, "<from_account>", esc(transaction.getAccountNumber()), "</from_account>");
        }
        xml.append("    </t_from_my_client>\n");

        xml.append("    <t_to_my_client>\n");
        appendLine(xml, 3, "<to_funds_code>C</to_funds_code>");
        appendLine(xml, 3, "<to_country>KE</to_country>");
        if (transaction.getMerchantId() != null) {
            appendLine(xml, 3, "<to_entity_id>", esc(transaction.getMerchantId()), "</to_entity_id>");
        }
        if (transaction.getMerchantName() != null) {
            appendLine(xml, 3, "<to_name>", esc(transaction.getMerchantName()), "</to_name>");
        }
        xml.append("    </t_to_my_client>\n");

        xml.append("  </transaction>\n");
        xml.append("</report>");

        String result = xml.toString();
        log.debug("CTR generated ({} chars) for transaction id={}", result.length(), transaction.getTransactionId());
        return result;
    }

    // =========================================================================
    // ANNREP — Annual Compliance Report
    // =========================================================================

    /**
     * Generates a goAML Annual Compliance Report (ANNREP) summary XML for the
     * current calendar year, deriving metrics from the compliance-case repository.
     *
     * <p>The report period covers 1 Jan – 31 Dec of the current year (up to today
     * for in-progress years). Counters reflect the state of the database at the
     * time of generation.
     *
     * @return well-formed XML string ready for FRC submission.
     */
    public String generateAnnualComplianceReport() {
        log.info("Generating goAML Annual Compliance Report (ANNREP)");

        int currentYear = LocalDate.now().getYear();
        LocalDateTime periodStart = LocalDate.of(currentYear, 1, 1).atStartOfDay();
        LocalDateTime periodEnd   = LocalDate.of(currentYear, 12, 31).atTime(23, 59, 59);

        // Summary metrics from repository
        long totalCasesThisYear = complianceCaseRepository.countByCreatedAtBetween(periodStart, periodEnd);
        long strsFiled = complianceCaseRepository.countByStatus(CaseStatus.CLOSED_SAR_FILED);
        long clearedCases = complianceCaseRepository.countByStatus(CaseStatus.CLOSED_CLEARED);
        long blockedCases = complianceCaseRepository.countByStatus(CaseStatus.CLOSED_BLOCKED);
        long openCases = complianceCaseRepository.countByStatus(CaseStatus.IN_PROGRESS)
                + complianceCaseRepository.countByStatus(CaseStatus.NEW)
                + complianceCaseRepository.countByStatus(CaseStatus.ASSIGNED)
                + complianceCaseRepository.countByStatus(CaseStatus.PENDING_REVIEW)
                + complianceCaseRepository.countByStatus(CaseStatus.PENDING_INFO);

        String today = LocalDate.now().format(DATE_FMT);
        String fiuRef = buildFiuRef("ANNREP", null);

        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<report xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");

        appendLine(xml, 1, "<rentity_id>", esc(frcEntityId), "</rentity_id>");
        appendLine(xml, 1, "<rentity_r_name>", esc(frcEntityName), "</rentity_r_name>");
        appendLine(xml, 1, "<submission_code>E</submission_code>");
        appendLine(xml, 1, "<report_code>ANNREP</report_code>");
        appendLine(xml, 1, "<entity_reference>ANNREP-", String.valueOf(currentYear), "</entity_reference>");
        appendLine(xml, 1, "<fiu_ref_number>", esc(fiuRef), "</fiu_ref_number>");
        appendLine(xml, 1, "<submission_date>", today, "</submission_date>");
        appendLine(xml, 1, "<currency_code_local>KES</currency_code_local>");

        xml.append("  <report_period>\n");
        appendLine(xml, 2, "<period_start>", periodStart.toLocalDate().format(DATE_FMT), "</period_start>");
        appendLine(xml, 2, "<period_end>", LocalDate.now().format(DATE_FMT), "</period_end>");
        appendLine(xml, 2, "<report_year>", String.valueOf(currentYear), "</report_year>");
        xml.append("  </report_period>\n");

        xml.append("  <summary_statistics>\n");
        appendLine(xml, 2, "<total_cases_opened>", String.valueOf(totalCasesThisYear), "</total_cases_opened>");
        appendLine(xml, 2, "<str_filed>", String.valueOf(strsFiled), "</str_filed>");
        appendLine(xml, 2, "<cases_cleared>", String.valueOf(clearedCases), "</cases_cleared>");
        appendLine(xml, 2, "<cases_blocked>", String.valueOf(blockedCases), "</cases_blocked>");
        appendLine(xml, 2, "<cases_open_at_period_end>", String.valueOf(openCases), "</cases_open_at_period_end>");
        xml.append("  </summary_statistics>\n");

        xml.append("  <reporting_entity_details>\n");
        appendLine(xml, 2, "<entity_id>", esc(frcEntityId), "</entity_id>");
        appendLine(xml, 2, "<entity_name>", esc(frcEntityName), "</entity_name>");
        appendLine(xml, 2, "<country>KE</country>");
        appendLine(xml, 2, "<submission_type>ELECTRONIC</submission_type>");
        xml.append("  </reporting_entity_details>\n");

        xml.append("</report>");

        String result = xml.toString();
        log.info("ANNREP generated ({} chars), year={}, totalCases={}, strsFiled={}",
                result.length(), currentYear, totalCasesThisYear, strsFiled);
        return result;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Appends an indented XML element line to the builder.
     * Parts are concatenated without additional escaping — callers must pre-escape
     * content using {@link #esc(String)}.
     */
    private static void appendLine(StringBuilder sb, int indent, String... parts) {
        sb.append("  ".repeat(indent));
        for (String part : parts) {
            sb.append(part);
        }
        sb.append('\n');
    }

    /**
     * Returns {@code value} if non-null and non-blank, otherwise {@code fallback}.
     */
    private static String safe(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    /**
     * XML-escapes a string for safe embedding in element content.
     * Returns an empty string for null input.
     */
    @SuppressWarnings("deprecation")
    private static String esc(String value) {
        return StringEscapeUtils.escapeXml11(value != null ? value : "");
    }

    /**
     * Formats a LocalDateTime to an ISO-8601 date string.
     * Returns today's date if the input is null.
     */
    private static String formatDate(LocalDateTime dt) {
        return dt != null ? dt.toLocalDate().format(DATE_FMT) : LocalDate.now().format(DATE_FMT);
    }

    /**
     * Builds a unique FIU reference number in the format {@code TYPE-YYYYMMDD-UUID8}.
     */
    private static String buildFiuRef(String type, Long caseId) {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = caseId != null
                ? String.valueOf(caseId)
                : UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return type + "-" + datePart + "-" + suffix;
    }

    /**
     * Maps a transaction type string to a goAML mode-of-payment code.
     * <ul>
     *   <li>C — Cash</li>
     *   <li>T — Transfer</li>
     *   <li>E — Electronic/card</li>
     *   <li>X — Other</li>
     * </ul>
     */
    private static String deriveModeOfPayment(String transactionType) {
        if (transactionType == null) return "X";
        return switch (transactionType.toUpperCase()) {
            case "CASH", "CASH_DEPOSIT", "CASH_WITHDRAWAL" -> "C";
            case "WIRE_TRANSFER", "BANK_TRANSFER", "TRANSFER" -> "T";
            case "CARD", "DEBIT_CARD", "CREDIT_CARD", "MOBILE_MONEY" -> "E";
            default -> "X";
        };
    }

    /**
     * Builds a human-readable CTR transaction description from available fields.
     */
    private static String buildCtrDescription(Transaction tx) {
        StringBuilder sb = new StringBuilder();
        if (tx.getTransactionType() != null) {
            sb.append(tx.getTransactionType()).append(" transaction");
        } else {
            sb.append("Cash transaction");
        }
        if (tx.getMerchantName() != null) {
            sb.append(" at ").append(tx.getMerchantName());
        }
        if (tx.getCountryCode() != null) {
            sb.append(" [").append(tx.getCountryCode()).append("]");
        }
        return sb.toString();
    }
}
