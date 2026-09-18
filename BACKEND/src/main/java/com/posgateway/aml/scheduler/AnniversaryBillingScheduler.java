package com.posgateway.aml.scheduler;

import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.billing.CardBillingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

/** Charges payable invoices once each year on the PSP creation anniversary. */
@Component
public class AnniversaryBillingScheduler {
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
                            .forEach(cardBillingService::charge);
                    invoices.findByPsp_PspIdAndStatus(psp.getPspId(), "OVERDUE")
                            .forEach(cardBillingService::charge);
                });
    }
}
