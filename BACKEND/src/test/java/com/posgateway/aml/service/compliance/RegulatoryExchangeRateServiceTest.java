package com.posgateway.aml.service.compliance;

import com.posgateway.aml.entity.billing.CurrencyRate;
import com.posgateway.aml.repository.CurrencyRateRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegulatoryExchangeRateServiceTest {

    @Test
    void convertsWithApprovedFreshRateAndReturnsProvenance() {
        CurrencyRateRepository repository = mock(CurrencyRateRepository.class);
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 16, 12, 0);
        CurrencyRate kes = rate(asOf.minusHours(2), true);
        when(repository.findById("KES")).thenReturn(Optional.of(kes));
        RegulatoryExchangeRateService service = new RegulatoryExchangeRateService(repository, 72);

        RegulatoryExchangeRateService.ConversionResult result =
                service.convertToUsd(new BigDecimal("2000000"), "kes", asOf);

        assertTrue(result.available());
        assertEquals(new BigDecimal("15500.0000"), result.amountUsd());
        assertEquals("CENTRAL_BANK_FEED", result.source());
    }

    @Test
    void rejectsLegacyOrStaleRatesForRegulatoryDecisions() {
        CurrencyRateRepository repository = mock(CurrencyRateRepository.class);
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 16, 12, 0);
        CurrencyRate stale = rate(asOf.minusHours(80), true);
        when(repository.findById("KES")).thenReturn(Optional.of(stale));
        RegulatoryExchangeRateService service = new RegulatoryExchangeRateService(repository, 72);

        RegulatoryExchangeRateService.ConversionResult result =
                service.convertToUsd(new BigDecimal("2000000"), "KES", asOf);

        assertFalse(result.available());
        assertEquals("RATE_STALE", result.unavailableReason());
    }

    private CurrencyRate rate(LocalDateTime effectiveAt, boolean approved) {
        CurrencyRate rate = new CurrencyRate("KES", new BigDecimal("0.007750"));
        rate.setIsActive(true);
        rate.setRegulatoryApproved(approved);
        rate.setRateSource("CENTRAL_BANK_FEED");
        rate.setEffectiveAt(effectiveAt);
        return rate;
    }
}
