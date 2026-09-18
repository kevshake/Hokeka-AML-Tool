package com.posgateway.aml.service.psp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.psp.BillingRate;
import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.ApiUsageLogRepository;
import com.posgateway.aml.repository.BillingRateRepository;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.billing.BillingEmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the two invariants that make the monthly billing cycle safe to run automatically:
 *   1. Generation is idempotent — a second run for the same PSP+period returns the existing invoice
 *      and neither saves a duplicate nor re-consumes usage (no double-billing).
 *   2. A fresh run bills only un-invoiced usage, stamps those rows with the new invoice id, and
 *      totals the line items from the current effective rate.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceInvoiceTest {

    @Mock private BillingRateRepository billingRateRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PspRepository pspRepository;
    @Mock private ApiUsageLogRepository apiUsageLogRepository;
    @Mock private BillingEmailService billingEmailService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private BillingService service;

    private static final Long PSP_ID = 1L;
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);

    @Test
    void reRunReturnsExistingInvoiceAndDoesNotDoubleBill() {
        Psp psp = new Psp();
        psp.setPspCode("ACME");
        when(pspRepository.findById(PSP_ID)).thenReturn(Optional.of(psp));

        Invoice existing = Invoice.builder().invoiceNumber("INV-ACME-20260701-abcd").build();
        when(invoiceRepository.findByPspAndPeriod(eq(PSP_ID), any(), any()))
                .thenReturn(Optional.of(existing));

        Invoice result = service.generateMonthlyInvoice(PSP_ID, PERIOD_START);

        assertSame(existing, result, "must return the already-issued invoice");
        verify(invoiceRepository, never()).save(any());
        verify(apiUsageLogRepository, never()).markUsageInvoiced(anyLong(), anyLong(), any(), any());
        verify(billingEmailService, never()).sendInvoiceEmail(any());
    }

    @Test
    void freshRunBillsUninvoicedUsageAndStampsIt() {
        Psp psp = new Psp();
        psp.setPspCode("ACME");
        psp.setCurrency("USD");
        psp.setPaymentTerms(30);
        when(pspRepository.findById(PSP_ID)).thenReturn(Optional.of(psp));
        when(invoiceRepository.findByPspAndPeriod(eq(PSP_ID), any(), any())).thenReturn(Optional.empty());

        // One service line: 100 billable AML checks. Row shape = [serviceType, count, sumCost].
        when(apiUsageLogRepository.getUninvoicedUsageSummaryByService(eq(PSP_ID), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"AML_CHECK", 100L, new BigDecimal("1.00")}));

        BillingRate rate = new BillingRate();
        rate.setPricingModel("PER_REQUEST");
        rate.setBaseRate(new BigDecimal("0.05"));
        when(billingRateRepository.findActiveRateForPsp(eq(PSP_ID), eq("AML_CHECK"), any()))
                .thenReturn(Optional.of(rate));

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice i = inv.getArgument(0);
            i.setInvoiceId(555L);
            return i;
        });

        Invoice result = service.generateMonthlyInvoice(PSP_ID, PERIOD_START);

        // 100 checks * 0.05 = 5.00
        assertEquals(0, new BigDecimal("5.00").compareTo(result.getTotalAmount()));
        assertEquals(1, result.getLineItems().size());
        // The consumed usage rows must be stamped with THIS invoice's id so a re-run cannot re-bill.
        verify(apiUsageLogRepository).markUsageInvoiced(eq(PSP_ID), eq(555L), any(), any());
        verify(billingEmailService).sendInvoiceEmail(result);
    }
}
