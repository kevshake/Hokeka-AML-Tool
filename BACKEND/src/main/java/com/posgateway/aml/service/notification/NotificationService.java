package com.posgateway.aml.service.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationService.class);

    private final SlackService slackService;

    public NotificationService(SlackService slackService) {
        this.slackService = slackService;
    }

    /**
     * Send email notification (Mock)
     * 
     * @param to      Recipient email
     * @param subject Email subject
     * @param body    Email body
     */
    @Async
    public void sendEmail(String to, String subject, String body) {
        // In a real system, this would use JavaMailSender
        log.info("📧 [MOCK EMAIL] To: {}, Subject: {}\nBody: {}", to, subject, body);
    }

    /**
     * Send system alert (e.g., Slack/Teams)
     * 
     * @param channel Channel ID/Name
     * @param message Message content
     */
    @Async
    public void sendSystemAlert(String channel, String message) {
        log.info("🚨 [SYSTEM ALERT] Channel: {}, Message: {}", channel, message);
        slackService.sendAlert(message, "HIGH");
    }

    /**
     * Send alert with severity
     */
    @Async
    public void sendAlert(String message, String severity) {
        log.info("🚨 [ALERT] Severity: {}, Message: {}", severity, message);
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
        // In reality, this might POST to a merchant webhook or update a status in the
        // DB
        log.info("📡 [COMPLIANCE CALLBACK] Report ID: {}, Status: {}", reportId, status);
        // For demo, we also send a system alert
        sendSystemAlert("compliance-alerts", "FRC Report " + reportId + " status update: " + status);
    }
}
