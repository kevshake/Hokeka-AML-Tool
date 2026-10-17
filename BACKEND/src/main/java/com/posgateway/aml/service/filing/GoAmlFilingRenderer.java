package com.posgateway.aml.service.filing;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.posgateway.aml.util.PanRedactor;

/**
 * Renders the filed regulatory document (goAML-style XML, the format the SAR register already
 * exports) with the full transaction details pulled at filing time: amounts and currencies, times,
 * merchant, MCC, country, channel, the Edge decision with its rules and typologies, and sanctions
 * matches. Used only by the filing path: the rendered string is stored encrypted in
 * {@code regulatory_filed_documents} and is never logged.
 *
 * <p>Contract §2.3: narratives are workflow data; PAN-like values in a narrative (§2.5 Luhn guard)
 * are redacted before the document is stored.
 */
@Component
public class GoAmlFilingRenderer {

    /** goAML document format id stored with the document. */
    public static final String FORMAT = "GOAML_XML";

    /** MIME type of the document. */
    public static final String CONTENT_TYPE = "application/xml";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private final String entityId;
    private final String entityName;
    private final String localCurrency;

    /** Reporting-entity settings shared with the draft export. */
    public GoAmlFilingRenderer(@Value("${frc.reporting.entity-id:POSGATEWAY_AML}") String entityId,
                               @Value("${frc.reporting.entity-name:POS Gateway AML Platform}") String entityName,
                               @Value("${frc.reporting.local-currency:KES}") String localCurrency) {
        this.entityId = entityId;
        this.entityName = entityName;
        this.localCurrency = localCurrency;
    }

    /** Filing metadata common to SAR and CTR documents. */
    public record Filing(String filingRef, Instant filedAt, User filer, String regulatorReference, long pspId) {}

    // ── SAR / STR ────────────────────────────────────────────────────────────────────────────────

    /** The filed SAR/STR document. */
    public String renderSar(SuspiciousActivityReport sar, String kind, FilingExtract data, Filing f) {
        StringBuilder xml = new StringBuilder(4096);
        String jurisdiction = sar.getJurisdiction() != null ? sar.getJurisdiction() : "KE";
        String country = jurisdiction.length() == 2 ? jurisdiction : "KE";
        header(xml, kind, f, sar.getSarReference());

        ComplianceCase linkedCase = sar.getComplianceCase();
        xml.append("  <sar_details>\n");
        line(xml, 2, "sar_id", String.valueOf(sar.getId()));
        line(xml, 2, "sar_reference", f.filingRef());
        line(xml, 2, "sar_type", sar.getSarType() != null ? sar.getSarType().name() : "INITIAL");
        line(xml, 2, "status", "FILED");
        line(xml, 2, "jurisdiction", jurisdiction);
        line(xml, 2, "suspicious_activity_type",
                sar.getSuspiciousActivityType() != null ? sar.getSuspiciousActivityType() : "OTHER");
        Map<String, BigDecimal> totals = totals(data.transactions());
        BigDecimal total = sar.getTotalSuspiciousAmount() != null ? sar.getTotalSuspiciousAmount()
                : totals.size() == 1 && totals.containsKey(localCurrency) ? totals.get(localCurrency) : null;
        if (total != null) {
            line(xml, 2, "total_suspicious_amount", total.toPlainString());
        }
        xml.append("    <transaction_totals>\n");
        totals.forEach((ccy, sum) -> xml.append("      <total currency=\"").append(esc(ccy)).append("\">")
                .append(sum.toPlainString()).append("</total>\n"));
        xml.append("    </transaction_totals>\n");
        line(xml, 2, "transaction_count", String.valueOf(data.transactionCount()));
        Instant min = null;
        Instant max = null;
        for (FilingExtract.Txn t : data.transactions()) {
            if (t.evaluatedAt() != null) {
                min = min == null || t.evaluatedAt().isBefore(min) ? t.evaluatedAt() : min;
                max = max == null || t.evaluatedAt().isAfter(max) ? t.evaluatedAt() : max;
            }
        }
        String filedDate = date(f.filedAt());
        line(xml, 2, "activity_start_date", min != null ? date(min) : filedDate);
        line(xml, 2, "activity_end_date", max != null ? date(max) : filedDate);
        line(xml, 2, "psp_id", String.valueOf(f.pspId()));
        if (linkedCase != null && linkedCase.getCaseReference() != null) {
            line(xml, 2, "case_reference", linkedCase.getCaseReference());
        }
        if (linkedCase != null && linkedCase.getMerchantId() != null) {
            line(xml, 2, "merchant_id", String.valueOf(linkedCase.getMerchantId()));
        }
        if (f.regulatorReference() != null) {
            line(xml, 2, "filing_reference_number", f.regulatorReference());
        }
        if (sar.getFilingDeadline() != null) {
            line(xml, 2, "filing_deadline", sar.getFilingDeadline().toLocalDate().format(DATE));
        }
        if (sar.getAmendsSar() != null && sar.getAmendsSar().getSarReference() != null) {
            line(xml, 2, "amends_sar_reference", sar.getAmendsSar().getSarReference());
            if (sar.getAmendmentReason() != null) {
                line(xml, 2, "amendment_reason", redactPans(sar.getAmendmentReason()));
            }
        }
        line(xml, 2, "data_source", data.source());
        xml.append("  </sar_details>\n");

        officer(xml, f);
        xml.append("  <reason>\n");
        line(xml, 2, "s_indicator", "STRAC");
        line(xml, 2, "description", sar.getSuspiciousActivityType() != null ? sar.getSuspiciousActivityType() : "OTHER");
        line(xml, 2, "narrative", redactPans(sar.getNarrative() != null ? sar.getNarrative() : ""));
        xml.append("  </reason>\n");

        for (FilingExtract.Txn t : data.transactions()) {
            transaction(xml, t, country);
        }
        xml.append("</report>\n");
        return xml.toString();
    }

    // ── CTR ──────────────────────────────────────────────────────────────────────────────────────

    /** The filed CTR document: the cash aggregate and the anchor transaction. */
    public String renderCtr(ComplianceDeadline deadline, Long alertId, FilingExtract data, Filing f) {
        StringBuilder xml = new StringBuilder(4096);
        String jurisdiction = deadline.getJurisdiction() != null ? deadline.getJurisdiction() : "KE";
        String country = jurisdiction.length() == 2 ? jurisdiction : "KE";
        header(xml, "CTR", f, f.filingRef());
        FilingExtract.CtrAggregate a = data.ctr();
        xml.append("  <ctr_details>\n");
        line(xml, 2, "ctr_reference", f.filingRef());
        line(xml, 2, "deadline_id", String.valueOf(deadline.getId()));
        if (deadline.getDeadlineDate() != null) {
            line(xml, 2, "filing_deadline", deadline.getDeadlineDate().toLocalDate().format(DATE));
        }
        line(xml, 2, "jurisdiction", jurisdiction);
        line(xml, 2, "psp_id", String.valueOf(f.pspId()));
        if (alertId != null) {
            line(xml, 2, "alert_id", String.valueOf(alertId));
        }
        if (f.regulatorReference() != null) {
            line(xml, 2, "filing_reference_number", f.regulatorReference());
        }
        line(xml, 2, "data_source", data.source());
        xml.append("    <cash_aggregate basis=\"").append(esc(a.basis())).append("\">\n");
        line(xml, 3, "anchor_reference", a.anchorId());
        if (a.businessDate() != null) {
            line(xml, 3, "business_date", a.businessDate().format(DATE));
        }
        if (a.timezone() != null) {
            line(xml, 3, "business_day_timezone", a.timezone());
        }
        line(xml, 3, "reporting_currency", a.reportingCurrency());
        line(xml, 3, "cash_total", a.cashTotal().toPlainString());
        line(xml, 3, "reporting_threshold", a.threshold().toPlainString());
        line(xml, 3, "transaction_count", String.valueOf(a.txnCount()));
        if (a.fxAsOf() != null) {
            line(xml, 3, "fx_as_of", DATE_TIME.format(a.fxAsOf()));
        }
        if (a.fxSource() != null) {
            line(xml, 3, "fx_source", a.fxSource());
        }
        if (a.configVersion() != null) {
            line(xml, 3, "edge_config_version", String.valueOf(a.configVersion()));
        }
        xml.append("      <constituent_transactions>\n");
        for (String id : a.edgeAssessmentIds()) {
            line(xml, 4, "reference", id);
        }
        xml.append("      </constituent_transactions>\n");
        xml.append("    </cash_aggregate>\n");
        xml.append("  </ctr_details>\n");
        officer(xml, f);
        for (FilingExtract.Txn t : data.transactions()) {
            transaction(xml, t, country);
        }
        xml.append("</report>\n");
        return xml.toString();
    }

    // ── shared blocks ────────────────────────────────────────────────────────────────────────────

    /** Report root and reporting-entity header. */
    private void header(StringBuilder xml, String reportCode, Filing f, String entityReference) {
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<report xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n");
        line(xml, 1, "rentity_id", entityId);
        line(xml, 1, "rentity_r_name", entityName);
        line(xml, 1, "submission_code", "E");
        line(xml, 1, "report_code", reportCode);
        line(xml, 1, "entity_reference", entityReference != null ? entityReference : f.filingRef());
        line(xml, 1, "submission_date", date(f.filedAt()));
        line(xml, 1, "filing_date", date(f.filedAt()));
        line(xml, 1, "currency_code_local", localCurrency);
        line(xml, 1, "report_indicators", "CTR".equals(reportCode) ? "CTRAC" : "STRAC");
    }

    /** The filing officer block. */
    private static void officer(StringBuilder xml, Filing f) {
        xml.append("  <filing_officer>\n");
        User u = f.filer();
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String last = u.getLastName() != null ? u.getLastName() : "";
        String name = (first + " " + last).trim();
        line(xml, 2, "name", name.isEmpty() ? u.getUsername() : name);
        line(xml, 2, "username", u.getUsername());
        line(xml, 2, "filing_date", date(f.filedAt()));
        xml.append("  </filing_officer>\n");
    }

    /** One transaction block with the pulled details. */
    private void transaction(StringBuilder xml, FilingExtract.Txn t, String reportCountry) {
        BigDecimal amount = FilingDataService.major(t.amountMinor(), t.currency());
        xml.append("  <transaction>\n");
        line(xml, 2, "tran_id", t.number());
        if (t.edgeAssessmentId() != null) {
            line(xml, 2, "tran_reference", t.edgeAssessmentId());
        }
        if (t.legacyTxnId() != null) {
            line(xml, 2, "internal_ref_number", String.valueOf(t.legacyTxnId()));
        }
        if (t.evaluatedAt() != null) {
            line(xml, 2, "tran_date", date(t.evaluatedAt()));
            line(xml, 2, "tran_date_time", DATE_TIME.format(t.evaluatedAt()));
        }
        if (t.siteId() != null) {
            line(xml, 2, "transaction_location", t.siteId());
        }
        line(xml, 2, "tran_amount_original", amount.toPlainString());
        line(xml, 2, "tran_currency", t.currency());
        if (localCurrency.equals(t.currency())) {
            line(xml, 2, "tran_amount_local", amount.toPlainString());
        }
        line(xml, 2, "mode_of_payment", t.channel() != null ? t.channel() : "E");
        line(xml, 2, "from_funds_code", "Z");
        line(xml, 2, "to_funds_code", "Z");
        xml.append("    <t_from_my_client>\n");
        line(xml, 3, "from_funds_code", "Z");
        line(xml, 3, "from_country", reportCountry);
        xml.append("    </t_from_my_client>\n");
        xml.append("    <t_to_my_client>\n");
        line(xml, 3, "to_funds_code", "Z");
        line(xml, 3, "to_country", t.countryCode() != null ? t.countryCode() : reportCountry);
        if (t.merchantId() != null) {
            line(xml, 3, "to_entity_id", t.merchantId());
        }
        if (t.mcc() != null) {
            line(xml, 3, "to_entity_mcc", t.mcc());
        }
        xml.append("    </t_to_my_client>\n");
        xml.append("    <evaluation>\n");
        if (t.decision() != null) {
            line(xml, 3, "decision", t.decision());
        }
        if (t.score() != null) {
            line(xml, 3, "score", String.valueOf(t.score()));
        }
        for (Long rule : t.triggeredRuleIds()) {
            line(xml, 3, "triggered_rule_id", String.valueOf(rule));
        }
        for (String code : t.typologyCodes()) {
            line(xml, 3, "typology_code", code);
        }
        for (FilingExtract.SanctionsHit h : t.sanctionsHits()) {
            xml.append("      <sanctions_match>\n");
            line(xml, 4, "list_entry_id", h.entryId());
            line(xml, 4, "match_score", BigDecimal.valueOf(h.score()).stripTrailingZeros().toPlainString());
            if (h.matchedOn() != null) {
                line(xml, 4, "matched_on", h.matchedOn());
            }
            if (h.matchedValue() != null) {
                line(xml, 4, "matched_value", h.matchedValue());
            }
            if (h.listVersion() != null) {
                line(xml, 4, "list_version", h.listVersion());
            }
            xml.append("      </sanctions_match>\n");
        }
        if (!t.features().isEmpty()) {
            xml.append("      <features>\n");
            t.features().forEach((k, v) -> xml.append("        <feature name=\"").append(esc(k)).append("\">")
                    .append(esc(redactPans(v))).append("</feature>\n"));
            xml.append("      </features>\n");
        }
        line(xml, 3, "legal_hold", String.valueOf(t.legalHold()));
        xml.append("    </evaluation>\n");
        xml.append("  </transaction>\n");
    }

    /** Per-currency totals in major units, currencies in first-seen order. */
    static Map<String, BigDecimal> totals(List<FilingExtract.Txn> txns) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (FilingExtract.Txn t : txns) {
            out.merge(t.currency(), FilingDataService.major(t.amountMinor(), t.currency()), BigDecimal::add);
        }
        return out;
    }

    /** Replaces every PAN-like value (§2.5) with {@link PanRedactor#REDACTED}. */
    static String redactPans(String text) {
        return PanRedactor.redact(text);
    }

    /** One {@code <name>value</name>} element at an indent. */
    private static void line(StringBuilder sb, int indent, String name, String value) {
        sb.append("  ".repeat(indent)).append('<').append(name).append('>').append(esc(value)).append("</")
                .append(name).append(">\n");
    }

    /** XML 1.1 escaping. */
    private static String esc(String value) {
        return StringEscapeUtils.escapeXml11(value != null ? value : "");
    }

    /** UTC date of an instant. */
    private static String date(Instant at) {
        return DATE.format(at.atOffset(ZoneOffset.UTC));
    }
}
