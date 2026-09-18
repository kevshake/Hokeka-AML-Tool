package com.posgateway.aml.scheduler;

import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.service.billing.BillingEmailService;
import com.posgateway.aml.service.psp.PspService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dunning terminal action: a PSP whose invoice is overdue beyond the grace period is suspended
 * (once, only while ACTIVE); a PSP still within grace is left alone.
 */
@ExtendWith(MockitoExtension.class)
class DunningSchedulerSuspensionTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private BillingEmailService billingEmailService;
    @Mock private PspService pspService;
    @InjectMocks private DunningScheduler scheduler;

    @BeforeEach
    void config() {
        ReflectionTestUtils.setField(scheduler, "reminderIntervalDays", 7);
        ReflectionTestUtils.setField(scheduler, "suspendAfterDays", 45);
    }

    private Invoice overdue(long invoiceId, long pspId, LocalDate dueDate, String pspStatus) {
        Psp psp = new Psp();
        psp.setStatus(pspStatus);
        ReflectionTestUtils.setField(psp, "pspId", pspId);
        Invoice inv = Invoice.builder()
                .psp(psp)
                .invoiceNumber("INV-" + invoiceId)
                .status("OVERDUE")
                .dueDate(dueDate)
                .build();
        ReflectionTestUtils.setField(inv, "invoiceId", invoiceId);
        return inv;
    }

    @Test
    void suspendsChronicNonPayerButNotOneWithinGrace() {
        LocalDate today = LocalDate.now();
        Invoice chronic = overdue(1L, 100L, today.minusDays(50), "ACTIVE"); // beyond 45-day grace
        Invoice recent = overdue(2L, 200L, today.minusDays(10), "ACTIVE");  // within grace

        when(invoiceRepository.findOverdueInvoices(eq("SENT"), any(LocalDate.class))).thenReturn(List.of());
        when(invoiceRepository.findByStatus("OVERDUE")).thenReturn(List.of(chronic, recent));

        scheduler.runDunningCycle();

        verify(pspService).updatePspStatus(100L, "SUSPENDED");
        verify(pspService, never()).updatePspStatus(eq(200L), anyString());
    }

    @Test
    void doesNotReSuspendAnAlreadySuspendedTenant() {
        LocalDate today = LocalDate.now();
        Invoice chronic = overdue(3L, 300L, today.minusDays(90), "SUSPENDED");

        when(invoiceRepository.findOverdueInvoices(eq("SENT"), any(LocalDate.class))).thenReturn(List.of());
        when(invoiceRepository.findByStatus("OVERDUE")).thenReturn(List.of(chronic));

        scheduler.runDunningCycle();

        verify(pspService, never()).updatePspStatus(eq(300L), anyString());
    }
}
