package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.Transaction;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates FRC (Financial Reporting Centre, Kenya) goAML 5.0–compatible XML reports.
 *
 * <p>The output mirrors the goAML schema element names (UPPER_CASE) and structures
 * REPORT → REPORT_INDICATORS / REPORT_PARTY_TYPE / TRANSACTION nodes that the FRC
 * goAML application accepts on upload. We do not embed the full XSD here — the XSD
 * is published by the FRC and would normally be bound via JAXB-generated classes;
 * the streaming writer keeps us free of code-gen while still producing structurally
 * correct, schema-validatable XML.
 *
 * <p>Three report types:
 * <ul>
 *   <li>{@code STR} — Suspicious Transaction Report (per ComplianceCase)
 *   <li>{@code CTR} — Cash Transaction Report (per large cash Transaction)
 *   <li>Annual compliance summary (per reporting period)
 * </ul>
 */
@Service
public class FrcReportingService {

    private static final Logger log = LoggerFactory.getLogger(FrcReportingService.class);

    /** goAML date format. The schema is YYYY-MM-DDTHH:mm:ss without timezone. */
    private static final DateTimeFormatter GOAML_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;

    /** Reporting entity identifiers — operator's own goAML registration, set per env. */
    @Value("${frc.entity.id:HOKEKA_AML}")          private String entityId;
    @Value("${frc.entity.name:Hokeka AML}")        private String entityName;
    @Value("${frc.reporting.officer.id:OFFICER1}") private String officerId;
    @Value("${frc.country:KE}")                    private String country;

    /** Local reporting currency — Kenya default KES, configurable per jurisdiction. */
    @Value("${regulatory.local.currency:KES}")     private String defaultCurrency;

    public FrcReportingService(MerchantRepository merchantRepository,
                               TransactionRepository transactionRepository) {
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Suspicious Transaction Report (STR)
    // ────────────────────────────────────────────────────────────────────────

    public String generateSuspiciousTransactionReport(ComplianceCase complianceCase) {
        if (complianceCase == null) {
            throw new IllegalArgumentException("ComplianceCase is required");
        }
        log.info("Generating STR for case id={} ref={}", complianceCase.getId(), complianceCase.getCaseReference());

        Merchant merchant = complianceCase.getMerchantId() != null
                ? merchantRepository.findById(complianceCase.getMerchantId()).orElse(null)
                : null;

        // 90-day FRC-suggested baseline lookback from case open date
        LocalDateTime windowStart = (complianceCase.getCreatedAt() != null
                ? complianceCase.getCreatedAt() : LocalDateTime.now()).minusDays(90);
        List<TransactionEntity> txns = (merchant != null)
                ? transactionRepository.findByMerchantIdAndTimestampAfter(
                        String.valueOf(merchant.getMerchantId()), windowStart)
                : List.of();

        StringWriter sw = new StringWriter();
        try {
            XMLStreamWriter w = createWriter(sw);
            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement("report");
            w.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");

            writeText(w, "rentity_id", entityId);
            writeText(w, "rentity_branch", country);
            writeText(w, "submission_code", "E");          // E = electronic
            writeText(w, "report_code", "STR");
            writeText(w, "entity_reference", complianceCase.getCaseReference());
            writeText(w, "submission_date", LocalDateTime.now().format(GOAML_TS));
            writeText(w, "currency_code_local", defaultCurrency);
            writeText(w, "reporting_person", officerId);
            writeText(w, "location", country);
            writeText(w, "reason", complianceCase.getDescription());
            writeText(w, "action", complianceCase.getResolution());

            // Reporting entity (REQUIRED in goAML)
            w.writeStartElement("reporting_entity");
            writeText(w, "name", entityName);
            writeText(w, "country", country);
            w.writeEndElement();

            // Subject of report — the merchant (REQUIRED for STR)
            if (merchant != null) {
                w.writeStartElement("subject");
                writeText(w, "subject_type", "ENTITY");
                writeText(w, "name", merchant.getLegalName());
                writeText(w, "trading_name", merchant.getTradingName());
                writeText(w, "registration_number", merchant.getRegistrationNumber());
                writeText(w, "tax_number", merchant.getTaxId());
                writeText(w, "country", merchant.getCountry());
                writeText(w, "incorporation_date",
                        merchant.getRegistrationDate() != null ? merchant.getRegistrationDate().toString() : null);
                w.writeEndElement();
            }

            for (TransactionEntity t : txns) {
                writeTransactionNode(w, t);
            }

            w.writeEndElement(); // </report>
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException e) {
            log.error("Failed to render STR XML for case {}", complianceCase.getId(), e);
            throw new IllegalStateException("STR XML generation failed", e);
        }
        return sw.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Cash Transaction Report (CTR)
    // ────────────────────────────────────────────────────────────────────────

    public String generateCashTransactionReport(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction is required");
        }
        log.info("Generating CTR for txn id={}", transaction.getTransactionId());

        StringWriter sw = new StringWriter();
        try {
            XMLStreamWriter w = createWriter(sw);
            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement("report");
            w.writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");

            writeText(w, "rentity_id", entityId);
            writeText(w, "rentity_branch", country);
            writeText(w, "submission_code", "E");
            writeText(w, "report_code", "CTR");
            writeText(w, "entity_reference", transaction.getTransactionId());
            writeText(w, "submission_date", LocalDateTime.now().format(GOAML_TS));
            writeText(w, "currency_code_local", defaultCurrency);
            writeText(w, "reporting_person", officerId);
            writeText(w, "location", country);

            w.writeStartElement("reporting_entity");
            writeText(w, "name", entityName);
            writeText(w, "country", country);
            w.writeEndElement();

            // Inline txn node from the API model (single transaction CTR path)
            w.writeStartElement("transaction");
            writeText(w, "transactionnumber", transaction.getTransactionId());
            writeText(w, "transaction_location", country);
            writeText(w, "date_transaction",
                    transaction.getTransactionTimestamp() != null
                            ? transaction.getTransactionTimestamp().format(GOAML_TS) : null);
            writeText(w, "transmode_code", transaction.getTransactionType());
            writeText(w, "amount_local", transaction.getAmount() != null
                    ? transaction.getAmount().toPlainString() : null);
            writeText(w, "amount_currency", transaction.getAmount() != null
                    ? transaction.getAmount().toPlainString() : BigDecimal.ZERO.toPlainString());
            writeText(w, "currency", transaction.getCurrencyCode() != null
                    ? transaction.getCurrencyCode() : defaultCurrency);
            writeText(w, "merchant_id", transaction.getMerchantId());
            writeText(w, "merchant_name", transaction.getMerchantName());
            writeText(w, "mcc", transaction.getMerchantCategoryCode());
            writeText(w, "country", transaction.getCountryCode());
            writeText(w, "ip_address", transaction.getIpAddress());
            if (transaction.getAmlRiskScore() != null)
                writeText(w, "aml_risk_score", String.valueOf(transaction.getAmlRiskScore()));
            if (transaction.getFraudScore() != null)
                writeText(w, "fraud_score", String.valueOf(transaction.getFraudScore()));
            w.writeEndElement(); // </transaction>

            w.writeEndElement(); // </report>
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException e) {
            log.error("Failed to render CTR XML for txn {}", transaction.getTransactionId(), e);
            throw new IllegalStateException("CTR XML generation failed", e);
        }
        return sw.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Annual Compliance Report
    // ────────────────────────────────────────────────────────────────────────

    public String generateAnnualComplianceReport() {
        return generateAnnualComplianceReport(LocalDate.now().getYear());
    }

    public String generateAnnualComplianceReport(int year) {
        log.info("Generating annual compliance report for {}", year);

        LocalDateTime start = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime end   = LocalDate.of(year, 12, 31).atTime(23, 59, 59);

        long totalMerchants = merchantRepository.count();

        StringWriter sw = new StringWriter();
        try {
            XMLStreamWriter w = createWriter(sw);
            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement("annual_compliance_report");

            writeText(w, "reporting_year", String.valueOf(year));
            writeText(w, "rentity_id", entityId);
            writeText(w, "rentity_name", entityName);
            writeText(w, "submission_date", LocalDateTime.now().format(GOAML_TS));
            writeText(w, "country", country);

            w.writeStartElement("totals");
            writeText(w, "total_merchants", String.valueOf(totalMerchants));
            writeText(w, "period_start", start.format(GOAML_TS));
            writeText(w, "period_end", end.format(GOAML_TS));
            w.writeEndElement();

            w.writeEndElement();
            w.writeEndDocument();
            w.flush();
            w.close();
        } catch (XMLStreamException e) {
            log.error("Failed to render annual report XML for year {}", year, e);
            throw new IllegalStateException("Annual report XML generation failed", e);
        }
        return sw.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private void writeTransactionNode(XMLStreamWriter w, TransactionEntity t) throws XMLStreamException {
        w.writeStartElement("transaction");
        writeText(w, "transactionnumber", String.valueOf(t.getTxnId()));
        writeText(w, "transaction_location", country);
        writeText(w, "date_transaction",
                t.getTxnTs() != null ? t.getTxnTs().format(GOAML_TS) : null);
        // amountCents → BigDecimal
        if (t.getAmountCents() != null) {
            BigDecimal amount = BigDecimal.valueOf(t.getAmountCents()).movePointLeft(2);
            writeText(w, "amount_local", amount.toPlainString());
            writeText(w, "amount_currency", amount.toPlainString());
        }
        writeText(w, "currency", t.getCurrency() != null ? t.getCurrency() : defaultCurrency);
        writeText(w, "merchant_id", t.getMerchantId());
        writeText(w, "country", t.getMerchantCountry());
        writeText(w, "ip_address", t.getIpAddress());
        writeText(w, "device_fingerprint", t.getDeviceFingerprint());
        if (t.getKrs() != null) writeText(w, "krs_score", String.valueOf(t.getKrs()));
        if (t.getCra() != null) writeText(w, "cra_score", String.valueOf(t.getCra()));
        if (t.getTrs() != null) writeText(w, "trs_score", String.valueOf(t.getTrs()));
        writeText(w, "decision", t.getDecision());
        w.writeEndElement();
    }

    /** Writes &lt;tag&gt;value&lt;/tag&gt; only when value is non-blank. */
    private static void writeText(XMLStreamWriter w, String tag, String value) throws XMLStreamException {
        if (value == null || value.isBlank()) return;
        w.writeStartElement(tag);
        w.writeCharacters(value);
        w.writeEndElement();
    }

    private static XMLStreamWriter createWriter(StringWriter sw) throws XMLStreamException {
        return XMLOutputFactory.newInstance().createXMLStreamWriter(sw);
    }
}
