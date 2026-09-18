package com.posgateway.aml.scheduler;

import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.billing.CardBillingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Charges payable invoices once each year on the PSP creation anniversary. */
@Component
public class AnniversaryBillingScheduler {
    private static final Logger log = LoggerFactory.getLogger(AnniversaryBillingScheduler.class);
    private final PspRepository psps;
    private final InvoiceRepository invoices;
    private final CardBillingService cardBillingService;

    public AnniversaryBillingScheduler(PspRepository psps, InvoiceRepository invoices,
            CardBillingService cardBillingService) {
        this.psps = psps;
        this.invoices = invoices;
        this.cardBillingService = cardBillingService;
    }

    @Scheduled(cron = "${billing.anniversary.cron:0 30 2 * * *}")
    public void chargeAnnualAccounts() {
        LocalDate today = LocalDate.now();
        psps.findAll().stream()
                .filter(psp -> psp.getCreatedAt() != null
                        && psp.getCreatedAt().toLocalDate().isBefore(today)
                        && psp.getCreatedAt().getMonth() == today.getMonth()
                        && psp.getCreatedAt().getDayOfMonth() == today.getDayOfMonth())
                .forEach(psp -> {
                    invoices.findByPsp_PspIdAndStatus(psp.getPspId(), "SENT")
                            .forEach(this::chargeSafely);
                    invoices.findByPsp_PspIdAndStatus(psp.getPspId(), "OVERDUE")
                            .forEach(this::chargeSafely);
                });
    }

    private void chargeSafely(com.posgateway.aml.entity.psp.Invoice invoice) {
        try {
            cardBillingService.charge(invoice);
        } catch (Exception failure) {
            log.warn("Annual card charge could not start for invoice {}: {}",
                    invoice.getInvoiceId(), failure.getMessage());
        }
    }
}
