package com.posgateway.aml.service.billing;

import com.posgateway.aml.entity.psp.Invoice;
import com.posgateway.aml.entity.psp.InvoiceLineItem;
import com.posgateway.aml.entity.psp.Psp;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Async email service for billing events: invoice delivery and overdue dunning.
 * <p>
 * All send methods are {@code @Async} and fail-soft (catch + log, never throw).
 * JavaMailSender is optional — when absent the service logs a warning and returns.
 */
@Service
public class BillingEmailService {

    private static final Logger log = LoggerFactory.getLogger(BillingEmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final JavaMailSender mailSender;
    private final InvoicePdfService invoicePdfService;

    @Value("${notifications.from-address:no-reply@hokeka.com}")
    private String fromAddress;

    @Value("${notifications.email-enabled:true}")
    private boolean emailEnabled;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${billing.invoice.company-name:Hokeka AML Platform}")
    private String companyName;

    @Value("${billing.invoice.support-email:billing@hokeka.com}")
    private String supportEmail;

    @Value("${billing.dunning.reminder-interval-days:7}")
    private int reminderIntervalDays;

    /**
     * Constructor: JavaMailSender is optional — if absent all send methods short-circuit gracefully.
     */
    public BillingEmailService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) JavaMailSender mailSender,
            InvoicePdfService invoicePdfService) {
        this.mailSender = mailSender;
        this.invoicePdfService = invoicePdfService;
    }

    // -------------------------------------------------------------------------
    // Public send methods
    // -------------------------------------------------------------------------

    /**
     * Send the invoice to the PSP contact email with a PDF attachment.
     * Runs asynchronously; never throws.
     */
    @Async
    public void sendInvoiceEmail(Invoice invoice) {
        if (!canSend()) return;

        String to = resolveRecipient(invoice);
        if (to == null) return;

        String subject = "Invoice #" + invoice.getInvoiceNumber()
                + " for " + formatPeriod(invoice)
                + " — " + formatAmount(invoice.getTotalAmount(), invoice.getCurrency());
        try {
            byte[] pdf = invoicePdfService.generateInvoicePdf(invoice);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildInvoiceHtml(invoice), true);
            helper.addAttachment(
                    "invoice-" + invoice.getInvoiceNumber() + ".pdf",
                    new org.springframework.core.io.ByteArrayResource(pdf),
                    "application/pdf");
            mailSender.send(message);
            log.info("Invoice email sent to {} for invoice {}", to, invoice.getInvoiceNumber());
        } catch (MessagingException | MailException ex) {
            log.error("Failed to send invoice email for {} to {}: {}", invoice.getInvoiceNumber(), to, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error sending invoice email for {}: {}", invoice.getInvoiceNumber(), ex.getMessage(), ex);
        }
    }

    /**
     * Send an overdue payment reminder to the PSP.
     * Runs asynchronously; never throws.
     */
    @Async
    public void sendDunningReminderEmail(Invoice invoice) {
        if (!canSend()) return;

        String to = resolveRecipient(invoice);
        if (to == null) return;

        String subject = "Payment Reminder — Invoice #" + invoice.getInvoiceNumber()
                + " is Overdue (" + formatAmount(invoice.getTotalAmount(), invoice.getCurrency()) + ")";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(buildDunningHtml(invoice, false), true);
            mailSender.send(message);
            log.info("Dunning reminder sent to {} for invoice {}", to, invoice.getInvoiceNumber());
        } catch (MessagingException | MailException ex) {
            log.error("Failed to send dunning reminder for {} to {}: {}", invoice.getInvoiceNumber(), to, ex.getMessage());
        }
    }

    /**
     * Send an escalation email to both the PSP and the platform admin.
     * Runs asynchronously; never throws.
     */
    @Async
    public void sendEscalationEmail(Invoice invoice, String adminEmail) {
        if (!canSend()) return;

        String pspTo = resolveRecipient(invoice);
        String subject = "ESCALATION — Invoice #" + invoice.getInvoiceNumber()
                + " overdue > 30 days (" + formatAmount(invoice.getTotalAmount(), invoice.getCurrency()) + ")";
        String html = buildDunningHtml(invoice, true);

        sendSingleEmail(pspTo, subject, html, "escalation (PSP)", invoice.getInvoiceNumber());
        if (adminEmail != null && !adminEmail.isBlank()) {
            sendSingleEmail(adminEmail, "[Admin] " + subject, html, "escalation (admin)", invoice.getInvoiceNumber());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void sendSingleEmail(String to, String subject, String html, String label, String invoiceNumber) {
        if (to == null || to.isBlank()) return;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent {} email for invoice {} to {}", label, invoiceNumber, to);
        } catch (MessagingException | MailException ex) {
            log.error("Failed {} email for invoice {} to {}: {}", label, invoiceNumber, to, ex.getMessage());
        }
    }

    private boolean canSend() {
        if (!emailEnabled || mailHost == null || mailHost.isBlank() || mailSender == null) {
            log.debug("Billing email skipped — email disabled or SMTP not configured");
            return false;
        }
        return true;
    }

    private String resolveRecipient(Invoice invoice) {
        Psp psp = invoice.getPsp();
        if (psp == null) {
            log.warn("Invoice {} has no associated PSP — cannot send email", invoice.getInvoiceNumber());
            return null;
        }
        String email = psp.getContactEmail();
        if (email == null || email.isBlank()) {
            log.warn("PSP {} has no contact email — cannot send invoice email for {}", psp.getPspCode(), invoice.getInvoiceNumber());
            return null;
        }
        return email;
    }

    private String buildInvoiceHtml(Invoice invoice) {
        Psp psp = invoice.getPsp();
        String pspName = psp.getTradingName() != null && !psp.getTradingName().isBlank()
                ? psp.getTradingName() : psp.getLegalName();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#333;margin:0;padding:0;\">");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f4f4;\"><tr><td align=\"center\" style=\"padding:30px 0;\">");
        sb.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fff;border-radius:8px;overflow:hidden;\">");

        // Header band
        sb.append("<tr><td style=\"background:#8B4049;padding:24px 32px;\">");
        sb.append("<h1 style=\"color:#fff;margin:0;font-size:22px;\">").append(escape(companyName)).append("</h1>");
        sb.append("<p style=\"color:#f0c0c5;margin:4px 0 0;\">Invoice Notification</p></td></tr>");

        // Body
        sb.append("<tr><td style=\"padding:32px;\">");
        sb.append("<p>Dear ").append(escape(pspName)).append(",</p>");
        sb.append("<p>Please find attached your invoice <strong>#").append(escape(invoice.getInvoiceNumber())).append("</strong> ");
        sb.append("for the period <strong>").append(formatPeriod(invoice)).append("</strong>.</p>");

        // Summary table
        sb.append("<table width=\"100%\" cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse:collapse;margin:20px 0;\">");
        sb.append("<tr style=\"background:#f9f0f1;\"><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Invoice #</th>");
        sb.append("<td style=\"border:1px solid #e0d0d1;\">").append(escape(invoice.getInvoiceNumber())).append("</td></tr>");
        sb.append("<tr><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Period</th>");
        sb.append("<td style=\"border:1px solid #e0d0d1;\">").append(formatPeriod(invoice)).append("</td></tr>");
        sb.append("<tr style=\"background:#f9f0f1;\"><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Due Date</th>");
        sb.append("<td style=\"border:1px solid #e0d0d1;\">").append(invoice.getDueDate() != null ? invoice.getDueDate().format(DATE_FMT) : "—").append("</td></tr>");
        sb.append("<tr><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Currency</th>");
        sb.append("<td style=\"border:1px solid #e0d0d1;\">").append(escape(invoice.getCurrency())).append("</td></tr>");

        // Line items summary
        if (!invoice.getLineItems().isEmpty()) {
            sb.append("<tr style=\"background:#f9f0f1;\"><th align=\"left\" style=\"border:1px solid #e0d0d1;\" colspan=\"2\">Services</th></tr>");
            for (InvoiceLineItem item : invoice.getLineItems()) {
                sb.append("<tr><td style=\"border:1px solid #e0d0d1;padding-left:16px;\">").append(escape(item.getDescription())).append("</td>");
                sb.append("<td style=\"border:1px solid #e0d0d1;\">").append(formatAmount(item.getLineTotal(), invoice.getCurrency())).append("</td></tr>");
            }
        }

        // Total
        sb.append("<tr style=\"background:#8B4049;\"><th align=\"left\" style=\"border:1px solid #6d3038;color:#fff;\">Total Due</th>");
        sb.append("<td style=\"border:1px solid #6d3038;color:#fff;font-weight:bold;\">").append(formatAmount(invoice.getTotalAmount(), invoice.getCurrency())).append("</td></tr>");
        sb.append("</table>");

        sb.append("<p>The invoice PDF is attached to this email. Please remit payment by the due date.</p>");
        sb.append("<p>For any queries, reply to this email or contact <a href=\"mailto:").append(escape(supportEmail)).append("\">").append(escape(supportEmail)).append("</a>.</p>");
        sb.append("<p>Thank you for your business.</p>");
        sb.append("</td></tr>");

        // Footer
        sb.append("<tr><td style=\"background:#f9f0f1;padding:16px 32px;text-align:center;font-size:11px;color:#888;\">");
        sb.append(escape(companyName)).append(" — automated billing notification");
        sb.append("</td></tr></table></td></tr></table></body></html>");

        return sb.toString();
    }

    private String buildDunningHtml(Invoice invoice, boolean isEscalation) {
        Psp psp = invoice.getPsp();
        String pspName = psp.getTradingName() != null && !psp.getTradingName().isBlank()
                ? psp.getTradingName() : psp.getLegalName();

        String urgency = isEscalation ? "URGENT ESCALATION" : "Payment Reminder";
        String color   = isEscalation ? "#c0392b" : "#8B4049";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#333;\">");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f4f4;\"><tr><td align=\"center\" style=\"padding:30px 0;\">");
        sb.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#fff;border-radius:8px;\">");

        sb.append("<tr><td style=\"background:").append(color).append(";padding:24px 32px;\">");
        sb.append("<h1 style=\"color:#fff;margin:0;font-size:20px;\">").append(urgency).append("</h1>");
        sb.append("<p style=\"color:#f0c0c5;margin:4px 0 0;\">").append(escape(companyName)).append("</p></td></tr>");

        sb.append("<tr><td style=\"padding:32px;\">");
        sb.append("<p>Dear ").append(escape(pspName)).append(",</p>");

        if (isEscalation) {
            sb.append("<p style=\"color:#c0392b;font-weight:bold;\">Your invoice <strong>#")
              .append(escape(invoice.getInvoiceNumber()))
              .append("</strong> is more than 30 days overdue. Immediate action is required to avoid service suspension.</p>");
        } else {
            sb.append("<p>This is a reminder that invoice <strong>#").append(escape(invoice.getInvoiceNumber()))
              .append("</strong> is overdue. Please arrange payment at your earliest convenience.</p>");
        }

        sb.append("<table width=\"100%\" cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse:collapse;margin:20px 0;\">");
        sb.append("<tr style=\"background:#fdf0f1;\"><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Invoice #</th><td style=\"border:1px solid #e0d0d1;\">").append(escape(invoice.getInvoiceNumber())).append("</td></tr>");
        sb.append("<tr><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Period</th><td style=\"border:1px solid #e0d0d1;\">").append(formatPeriod(invoice)).append("</td></tr>");
        sb.append("<tr style=\"background:#fdf0f1;\"><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Original Due Date</th><td style=\"border:1px solid #e0d0d1;\">").append(invoice.getDueDate() != null ? invoice.getDueDate().format(DATE_FMT) : "—").append("</td></tr>");
        sb.append("<tr><th align=\"left\" style=\"border:1px solid #e0d0d1;\">Status</th><td style=\"border:1px solid #e0d0d1;color:#c0392b;font-weight:bold;\">OVERDUE</td></tr>");
        sb.append("<tr style=\"background:").append(color).append(";\"><th align=\"left\" style=\"border:1px solid #6d3038;color:#fff;\">Amount Outstanding</th><td style=\"border:1px solid #6d3038;color:#fff;font-weight:bold;\">").append(formatAmount(invoice.getTotalAmount(), invoice.getCurrency())).append("</td></tr>");
        sb.append("</table>");

        sb.append("<p>To settle this invoice or discuss payment arrangements, please contact us at ");
        sb.append("<a href=\"mailto:").append(escape(supportEmail)).append("\">").append(escape(supportEmail)).append("</a>.</p>");
        sb.append("</td></tr>");

        sb.append("<tr><td style=\"background:#f9f0f1;padding:16px 32px;text-align:center;font-size:11px;color:#888;\">");
        sb.append(escape(companyName)).append(" — automated billing notification");
        sb.append("</td></tr></table></td></tr></table></body></html>");

        return sb.toString();
    }

    private String formatPeriod(Invoice invoice) {
        String start = invoice.getBillingPeriodStart() != null ? invoice.getBillingPeriodStart().format(DATE_FMT) : "?";
        String end   = invoice.getBillingPeriodEnd()   != null ? invoice.getBillingPeriodEnd().format(DATE_FMT)   : "?";
        return start + " – " + end;
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return (currency != null ? currency : "") + " 0.00";
        return (currency != null ? currency : "") + " " + String.format("%,.2f", amount);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
