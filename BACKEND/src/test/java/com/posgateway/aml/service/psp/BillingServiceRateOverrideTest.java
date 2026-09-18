package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.psp.BillingRate;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.*;
import com.posgateway.aml.service.billing.BillingEmailService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingServiceRateOverrideTest {
    @Test
    void createsActivePspScopedOverride() {
        BillingRateRepository rates = mock(BillingRateRepository.class);
        PspRepository psps = mock(PspRepository.class);
        Psp psp = new Psp();
        psp.setPspId(3L);
        when(psps.findById(3L)).thenReturn(Optional.of(psp));
        when(rates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BillingService service = new BillingService(rates, mock(InvoiceRepository.class), psps,
                mock(ApiUsageLogRepository.class), mock(BillingEmailService.class), new ObjectMapper());
        BillingRate rate = BillingRate.builder().serviceType("WALLET_SCREENING")
                .pricingModel("PER_REQUEST").baseRate(new BigDecimal("0.05")).build();

        BillingRate saved = service.saveRateOverride(3L, rate);

        assertSame(psp, saved.getPsp());
        assertTrue(saved.getIsActive());
        assertNotNull(saved.getEffectiveFrom());
    }
}
