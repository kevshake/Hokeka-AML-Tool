package com.posgateway.aml.service.notification;

import com.posgateway.aml.exception.NotificationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationService.class);

    private final SlackService slackService;

    /**
     * Optional — only present when spring-boot-starter-mail is on the classpath
     * and {@code spring.mail.host} is configured. May be null in test profiles.
     */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${notifications.email-enabled:false}")
    private boolean emailEnabled;

    @Value("${notifications.from-address:no-reply@hokeka.com}")
    private String fromAddress;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public NotificationService(SlackService slackService) {
        this.slackService = slackService;
    }

    /**
     * Send email notification.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If {@code notifications.email-enabled=false} OR {@code spring.mail.host}
     *       is blank OR the mail sender bean is unavailable: logs a WARN and
     *       returns without throwing (preserves dev/test ergonomics).</li>
     *   <li>Otherwise: builds a {@link SimpleMailMessage} and sends via the
     *       configured {@link JavaMailSender}. On {@link MailException} the
     *       error is logged and rethrown as a {@link NotificationException}
     *       so callers can record the failure.</li>
     * </ul>
     *
     * @param to      Recipient email
     * @param subject Email subject
     * @param body    Email body (plain text)
     */
    @Async
    public void sendEmail(String to, String subject, String body) {
        boolean disabled = !emailEnabled
                || mailHost == null || mailHost.isBlank()
                || mailSender == null;

        if (disabled) {
            log.warn("Email disabled — would have sent to {} subject {}", to, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} subject {}", to, subject);
        } catch (MailException ex) {
            log.error("Failed to send email to {} subject {}: {}", to, subject, ex.getMessage(), ex);
            throw new NotificationException(
                    "Failed to send email to " + to + " (subject: " + subject + ")", ex);
        }
    }

    /**
     * Send system alert (e.g., Slack/Teams)
     *
     * @param channel Channel ID/Name
     * @param message Message content
     */
    @Async
    public void sendSystemAlert(String channel, String message) {
        log.info("[SYSTEM ALERT] Channel: {}, Message: {}", channel, message);
        slackService.sendAlert(message, "HIGH");
    }

    /**
     * Send alert with severity
     */
    @Async
    public void sendAlert(String message, String severity) {
        log.info("[ALERT] Severity: {}, Message: {}", severity, message);
        slackService.sendAlert(message, severity);
    }

    /**
     * Send callback notification for compliance report status (Phase 29)
     *
     * @param reportId The ID of the report
     * @param status   The status (e.g., SUBMITTED, REJECTED)
     */
    @Async
    public void sendComplianceReportCallback(String reportId, String status) {
        // In reality, this might POST to a merchant webhook or update a status in the DB
        log.info("[COMPLIANCE CALLBACK] Report ID: {}, Status: {}", reportId, status);
        // For demo, we also send a system alert
        sendSystemAlert("compliance-alerts", "FRC Report " + reportId + " status update: " + status);
    }
}
