package com.posgateway.aml.service.filing;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceDeadline;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The filed goAML document carries the pulled transaction details (R9). */
class GoAmlFilingRendererTest {

    private final GoAmlFilingRenderer renderer = new GoAmlFilingRenderer("FRC-ENT-1", "Example PSP", "KES");

    /** A filed transaction with the canonical contract fixture values. */
    static FilingExtract.Txn txn(String id, long amountMinor, String currency) {
        return new FilingExtract.Txn(id, null, "AUTH-2026-10-16-001207", "acme-eu",
                Instant.parse("2026-10-16T10:02:51.880Z"), "BLOCK", 100, "mrc_00421", "5411", amountMinor, currency,
                "KE", "AGENT", List.of(9001L), List.of("SANCTIONS_COUNTERPARTY"),
                List.of(new FilingExtract.SanctionsHit("ofac-sdn:36216", 0.97, "COUNTERPARTY_NAME",
                        "Example Trading LLC", "2026-10-16.1")),
                Map.of("device_id", "dv-77a1"), true);
    }

    /** A filing officer. */
    static User filer() {
        User u = new User();
        u.setId(12L);
        u.setUsername("mlro");
        u.setFirstName("Mary");
        u.setLastName("Lro");
        return u;
    }

    /** Verifies: amounts, currency, times, rules, sanctions and totals are in the SAR; PANs are redacted. */
    @Test
    void sarDocumentCarriesFullTransactionDetails() {
        SuspiciousActivityReport sar = new SuspiciousActivityReport();
        sar.setId(77L);
        sar.setSarReference("SAR-2026-0077");
        sar.setJurisdiction("KE");
        sar.setSuspiciousActivityType("SANCTIONS");
        sar.setNarrative("Counterparty paid with card 4111 1111 1111 1111 twice.");
        sar.setFilingDeadline(LocalDateTime.of(2026, 10, 18, 17, 0));
        FilingExtract data = new FilingExtract("EDGE", List.of(
                txn("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", 210_000_000L, "KES"),
                txn("5a7b9c1d-3e5f-4a6b-9c8d-7e6f5a4b3c2d", 12_345L, "USD")), null, List.of());

        String xml = renderer.renderSar(sar, "SAR", data, new GoAmlFilingRenderer.Filing("SAR-2026-0077",
                Instant.parse("2026-10-17T08:00:00Z"), filer(), "FRC/2026/991", 45L));

        assertThat(xml).contains("<report_code>SAR</report_code>", "<rentity_id>FRC-ENT-1</rentity_id>",
                "<tran_amount_original>2100000.00</tran_amount_original>", "<tran_currency>KES</tran_currency>",
                "<tran_amount_local>2100000.00</tran_amount_local>",
                "<tran_amount_original>123.45</tran_amount_original>", "<tran_currency>USD</tran_currency>",
                "<total currency=\"KES\">2100000.00</total>", "<total currency=\"USD\">123.45</total>",
                "<tran_date_time>2026-10-16T10:02:51</tran_date_time>", "<to_entity_id>mrc_00421</to_entity_id>",
                "<triggered_rule_id>9001</triggered_rule_id>", "<list_entry_id>ofac-sdn:36216</list_entry_id>",
                "<matched_value>Example Trading LLC</matched_value>", "<filing_reference_number>FRC/2026/991",
                "<transaction_count>2</transaction_count>", "<data_source>EDGE</data_source>",
                "<name>Mary Lro</name>", "<activity_start_date>2026-10-16</activity_start_date>");
        assertThat(xml).doesNotContain("4111 1111 1111 1111").contains("[REDACTED-PAN]");
        // Two currencies: no single local total is claimed.
        assertThat(xml).doesNotContain("<total_suspicious_amount>");
    }

    /** Verifies: the CTR document carries the cash aggregate, its constituents and the anchor transaction. */
    @Test
    void ctrDocumentCarriesTheCashAggregate() {
        ComplianceDeadline d = new ComplianceDeadline();
        d.setId(31L);
        d.setJurisdiction("KE");
        d.setDeadlineDate(LocalDateTime.of(2026, 10, 23, 17, 0));
        FilingExtract.CtrAggregate agg = new FilingExtract.CtrAggregate("CARD_BUSINESS_DAY",
                "c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", LocalDate.of(2026, 10, 16), "Africa/Nairobi", "USD",
                new BigDecimal("16170.00"), new BigDecimal("15000.00"), 3,
                List.of("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", "5a7b9c1d-3e5f-4a6b-9c8d-7e6f5a4b3c2d",
                        "3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44"),
                Instant.parse("2026-10-16T06:00:00Z"), null, 14L);
        FilingExtract data = new FilingExtract("EDGE",
                List.of(txn("c2d4e6f8-1a3b-4c5d-8e7f-9a0b1c2d3e4f", 210_000_000L, "KES")), agg, List.of());

        String xml = renderer.renderCtr(d, 501L, data, new GoAmlFilingRenderer.Filing("CTR-31",
                Instant.parse("2026-10-17T08:00:00Z"), filer(), null, 45L));

        assertThat(xml).contains("<report_code>CTR</report_code>", "<ctr_reference>CTR-31</ctr_reference>",
                "<cash_aggregate basis=\"CARD_BUSINESS_DAY\">", "<business_date>2026-10-16</business_date>",
                "<business_day_timezone>Africa/Nairobi</business_day_timezone>",
                "<reporting_currency>USD</reporting_currency>", "<cash_total>16170.00</cash_total>",
                "<reporting_threshold>15000.00</reporting_threshold>", "<transaction_count>3</transaction_count>",
                "<edge_config_version>14</edge_config_version>",
                "<reference>3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44</reference>", "<alert_id>501</alert_id>",
                "<tran_amount_original>2100000.00</tran_amount_original>");
    }
}
