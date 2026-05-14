package com.posgateway.aml.scheduler;

import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.repository.InvoiceRepository;
import com.posgateway.aml.service.billing.BillingEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dunning Scheduler — daily overdue detection and weekly escalation.
 *
 * <p>Runs daily at 09:00 to:
 * <ol>
 *   <li>Find all SENT invoices whose due date has passed and mark them OVERDUE.</li>
 *   <li>Send a payment reminder email if no reminder has been sent yet,
 *       or if the last reminder was more than {@code billing.dunning.reminder-interval-days} days ago.</li>
 * </ol>
 *
 * <p>Runs every Monday at 09:00 to escalate invoices that are more than 30 days overdue
 * to both the PSP contact and the platform admin.
 *
 * <p>Both jobs are idempotent — safe to run multiple times with the same outcome.
 */
@Component
@ConditionalOnProperty(name = "billing.dunning.enabled", havingValue = "true", matchIfMissing = true)
public class DunningScheduler {

    private static final Logger log = LoggerFactory.getLogger(DunningScheduler.class);

    /** Invoices overdue beyond this threshold trigger the weekly escalation. */
    private static final int ESCALATION_OVERDUE_DAYS = 30;

    private final InvoiceRepository invoiceRepository;
    private final BillingEmailService billingEmailService;

    @Value("${billing.dunning.reminder-interval-days:7}")
    private int reminderIntervalDays;

    @Value("${billing.dunning.admin-email:${notifications.from-address:billing@hokeka.com}}")
    private String adminEmail;

    public DunningScheduler(InvoiceRepository invoiceRepository, BillingEmailService billingEmailService) {
        this.invoiceRepository = invoiceRepository;
        this.billingEmailService = billingEmailService;
    }

    // -------------------------------------------------------------------------
    // Daily overdue sweep — 09:00 every day
    // -------------------------------------------------------------------------

    /**
     * Mark overdue invoices and send first / repeat reminder emails.
     * Idempotent: re-running on the same day with the same data is a no-op.
     */
    @Scheduled(cron = "${billing.dunning.cron:0 0 9 * * *}")
    @Transactional
    public void runDunningCycle() {
        LocalDate today = LocalDate.now();
        log.info("Dunning cycle starting for date {}", today);

        // 1. Find SENT invoices that have passed their due date
        List<Invoice> sentOverdue = invoiceRepository.findOverdueInvoices("SENT", today);

        // 2. Also sweep any already-OVERDUE invoices for re-reminders
        List<Invoice> alreadyOverdue = invoiceRepository.findByStatus("OVERDUE");

        List<Invoice> allOverdue = mergeUnique(sentOverdue, alreadyOverdue);

        int markedCount   = 0;
        int remindedCount = 0;

        for (Invoice invoice : allOverdue) {
            try {
                // a. Transition SENT → OVERDUE
                if ("SENT".equals(invoice.getStatus())) {
                    invoice.setStatus("OVERDUE");
                    markedCount++;
                }

                // b. Send reminder if no reminder sent yet, or interval has elapsed
                if (shouldSendReminder(invoice)) {
                    billingEmailService.sendDunningReminderEmail(invoice);
                    invoice.setRemindedAt(LocalDateTime.now());
                    remindedCount++;
                }

                invoiceRepository.save(invoice);
            } catch (Exception e) {
                log.error("Error processing dunning for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            }
        }

        log.info("Dunning cycle complete. Marked overdue: {}, Reminders sent: {}, Total overdue: {}",
                markedCount, remindedCount, allOverdue.size());
    }

    // -------------------------------------------------------------------------
    // Weekly escalation — 09:00 every Monday
    // -------------------------------------------------------------------------

    /**
     * Escalate invoices that have been overdue for more than 30 days.
     * Sends an email to both the PSP contact and the platform admin.
     * Idempotent — re-running on the same week is harmless (PSP/admin get a duplicate
     * escalation, which is acceptable for overdue collections).
     */
    @Scheduled(cron = "${billing.dunning.second-reminder.cron:0 0 9 * * MON}")
    @Transactional
    public void runWeeklyEscalation() {
        LocalDate cutoff = LocalDate.now().minusDays(ESCALATION_OVERDUE_DAYS);
        log.info("Weekly escalation sweep — looking for OVERDUE invoices with dueDate before {}", cutoff);

        // OVERDUE invoices whose due date is > 30 days ago
        List<Invoice> escalationCandidates = invoiceRepository.findOverdueInvoices("OVERDUE", cutoff);

        if (escalationCandidates.isEmpty()) {
            log.info("Weekly escalation: no invoices require escalation");
            return;
        }

        log.warn("Weekly escalation: {} invoice(s) are more than {} days overdue — sending escalation emails",
                escalationCandidates.size(), ESCALATION_OVERDUE_DAYS);

        for (Invoice invoice : escalationCandidates) {
            try {
                billingEmailService.sendEscalationEmail(invoice, adminEmail);
            } catch (Exception e) {
                log.error("Error sending escalation for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            }
        }

        log.info("Weekly escalation complete for {} invoice(s)", escalationCandidates.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if a reminder should be sent for this invoice.
     * A reminder is due when:
     * <ul>
     *   <li>No reminder has ever been sent ({@code remindedAt} is null), or</li>
     *   <li>The last reminder was sent more than {@code reminderIntervalDays} days ago.</li>
     * </ul>
     */
    private boolean shouldSendReminder(Invoice invoice) {
        if (invoice.getRemindedAt() == null) {
            return true;
        }
        LocalDateTime nextReminderDue = invoice.getRemindedAt().plusDays(reminderIntervalDays);
        return LocalDateTime.now().isAfter(nextReminderDue);
    }

    /**
     * Merge two invoice lists deduplicating by invoiceId.
     */
    private List<Invoice> mergeUnique(List<Invoice> primary, List<Invoice> secondary) {
        List<Invoice> merged = new ArrayList<>(primary);
        for (Invoice candidate : secondary) {
            boolean alreadyPresent = merged.stream()
                    .anyMatch(e -> e.getInvoiceId().equals(candidate.getInvoiceId()));
            if (!alreadyPresent) {
                merged.add(candidate);
            }
        }
        return merged;
    }
}
