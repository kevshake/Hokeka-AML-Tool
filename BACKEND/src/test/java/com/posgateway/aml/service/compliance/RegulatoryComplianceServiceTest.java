package com.posgateway.aml.service.compliance;

import com.posgateway.aml.compliance.RegulatoryComplianceService;
import com.posgateway.aml.repository.risk.CountryRiskRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegulatoryComplianceServiceTest {

    @Test
    void onlyCashTransactionsCanBecomeCtrReportable() {
        RegulatoryExchangeRateService exchange = mock(RegulatoryExchangeRateService.class);
        CountryRiskRepository countries = mock(CountryRiskRepository.class);
        when(countries.findByCountryCode("KE")).thenReturn(Optional.empty());
        RegulatoryComplianceService service = service(exchange, countries);

        RegulatoryComplianceService.ComplianceDecision decision = service.evaluateCompliance(
                new BigDecimal("20000"), "USD", "KE", false, 1, 0.1,
                false, LocalDateTime.now());

        assertFalse(decision.isCtrRequired());
        assertEquals("NOT_CASH", decision.getEvidence().get("ctrEvaluationStatus"));
    }

    @Test
    void approvedConversionCanProduceCtrAndEvidence() {
        RegulatoryExchangeRateService exchange = mock(RegulatoryExchangeRateService.class);
        CountryRiskRepository countries = mock(CountryRiskRepository.class);
        LocalDateTime effectiveAt = LocalDateTime.now().minusHours(1);
        when(exchange.convertToUsd(
                new BigDecimal("2000000"), "KES", effectiveAt.plusHours(1)))
                .thenReturn(new RegulatoryExchangeRateService.ConversionResult(
                        true, new BigDecimal("15500.0000"), new BigDecimal("0.007750"),
                        "CENTRAL_BANK_FEED", effectiveAt, null));
        when(countries.findByCountryCode("KE")).thenReturn(Optional.empty());
        RegulatoryComplianceService service = service(exchange, countries);

        RegulatoryComplianceService.ComplianceDecision decision = service.evaluateCompliance(
                new BigDecimal("2000000"), "KES", "KE", false, 1, 0.1,
                true, effectiveAt.plusHours(1));

        assertTrue(decision.isCtrRequired());
        assertEquals("REPORTABLE", decision.getEvidence().get("ctrEvaluationStatus"));
        assertEquals(new BigDecimal("15500.0000"), decision.getEvidence().get("amountUsd"));
    }

    @Test
    void unavailableFxHoldsCashTransactionForReview() {
        RegulatoryExchangeRateService exchange = mock(RegulatoryExchangeRateService.class);
        CountryRiskRepository countries = mock(CountryRiskRepository.class);
        LocalDateTime asOf = LocalDateTime.now();
        when(exchange.convertToUsd(new BigDecimal("2000000"), "KES", asOf))
                .thenReturn(new RegulatoryExchangeRateService.ConversionResult(
                        false, null, null, null, null, "RATE_NOT_APPROVED"));
        when(countries.findByCountryCode("KE")).thenReturn(Optional.empty());
        RegulatoryComplianceService service = service(exchange, countries);

        RegulatoryComplianceService.ComplianceDecision decision = service.evaluateCompliance(
                new BigDecimal("2000000"), "KES", "KE", false, 1, 0.1, true, asOf);

        assertEquals("HOLD", decision.getDecision());
        assertEquals("FX_UNAVAILABLE", decision.getEvidence().get("ctrEvaluationStatus"));
    }

    @Test
    void repeatedNearThresholdCashActivityCreatesStructuringHold() {
        RegulatoryExchangeRateService exchange = mock(RegulatoryExchangeRateService.class);
        CountryRiskRepository countries = mock(CountryRiskRepository.class);
        LocalDateTime asOf = LocalDateTime.now();
        when(exchange.convertToUsd(new BigDecimal("13000"), "USD", asOf))
                .thenReturn(new RegulatoryExchangeRateService.ConversionResult(
                        true, new BigDecimal("13000.0000"), BigDecimal.ONE,
                        "USD_PARITY", asOf, null));
        when(countries.findByCountryCode("KE")).thenReturn(Optional.empty());
        RegulatoryComplianceService service = service(exchange, countries);

        RegulatoryComplianceService.ComplianceDecision decision = service.evaluateCompliance(
                new BigDecimal("13000"), "USD", "KE", false, 2, 0.1, true, asOf);

        assertTrue(decision.isStrRequired());
        assertEquals("HOLD", decision.getDecision());
        assertTrue(decision.getReasons().containsKey("CASH_STRUCTURING_24H"));
    }

    private RegulatoryComplianceService service(
            RegulatoryExchangeRateService exchange, CountryRiskRepository countries) {
        return new RegulatoryComplianceService(
                exchange, countries, new BigDecimal("15000"), new BigDecimal("0.80"), true);
    }
}
