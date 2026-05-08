package com.posgateway.aml.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Real email notification service for AML/case lifecycle events.
 *
 * <p>Backed by Spring Mail's {@link JavaMailSender}. SMTP credentials are read
 * from {@code spring.mail.host/port/username/password} (env-overridable). When
 * any of host/sender/JavaMailSender are missing the service logs a WARN and
 * short-circuits — email failures must never break AML message processing.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${notifications.email-enabled:true}")
    private boolean emailEnabled;

    @Value("${notifications.from-address:no-reply@hokeka.com}")
    private String fromAddress;

    @Value("${notification.fallback.email:}")
    private String fallbackEmail;

    public EmailNotificationService(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Send an HTML email with the given subject and body to the given recipient.
     * <p>Fails soft — does not throw on missing SMTP / mail send error.
     */
    @Async
    public void sendHtml(String to, String subject, String htmlBody) {
        if (!emailEnabled || mailHost == null || mailHost.isBlank() || mailSender == null) {
            log.warn("Email disabled or SMTP unconfigured — would have sent to '{}' subject '{}'", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("Email recipient blank — skipping send (subject '{}')", subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {} subject '{}'", to, subject);
        } catch (MessagingException | MailException ex) {
            log.error("Failed to send email to {} subject '{}': {}", to, subject, ex.getMessage());
        }
    }

    /**
     * Compose and send a notification email driven by a JSON payload from Kafka.
     *
     * <p>Recognised JSON fields:
     * <ul>
     *   <li>{@code recipientUserId} — preferred recipient lookup</li>
     *   <li>{@code recipientEmail} — direct address override</li>
     *   <li>{@code subject} — email subject (falls back to {@code defaultSubject})</li>
     *   <li>{@code body}, {@code message}, {@code description} — body text</li>
     *   <li>{@code caseId}, {@code caseReference}, {@code status}, {@code priority} — context fields</li>
     * </ul>
     */
    public void sendFromJson(String json, String defaultSubject) {
        String to = null;
        String subject = defaultSubject;
        String bodyText = null;
        String caseRef = null;
        String status = null;
        String priority = null;

        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode recipUserIdNode = root.get("recipientUserId");
            if (recipUserIdNode != null && recipUserIdNode.canConvertToLong()) {
                Optional<User> u = userRepository.findById(recipUserIdNode.asLong());
                if (u.isPresent() && u.get().getEmail() != null) {
                    to = u.get().getEmail();
                }
            }
            if (to == null) {
                JsonNode recipEmail = root.get("recipientEmail");
                if (recipEmail != null && !recipEmail.asText().isBlank()) {
                    to = recipEmail.asText();
                }
            }
            if (root.hasNonNull("subject")) subject = root.get("subject").asText();
            if (root.hasNonNull("body"))    bodyText = root.get("body").asText();
            else if (root.hasNonNull("message")) bodyText = root.get("message").asText();
            else if (root.hasNonNull("description")) bodyText = root.get("description").asText();
            if (root.hasNonNull("caseReference")) caseRef = root.get("caseReference").asText();
            else if (root.hasNonNull("caseId")) caseRef = root.get("caseId").asText();
            if (root.hasNonNull("status"))   status = root.get("status").asText();
            if (root.hasNonNull("priority")) priority = root.get("priority").asText();
        } catch (Exception ex) {
            log.warn("Failed to parse notification JSON; using defaults. err={}", ex.getMessage());
            bodyText = json;
        }

        if (to == null || to.isBlank()) {
            if (fallbackEmail != null && !fallbackEmail.isBlank()) {
                log.warn("No recipient resolved from payload; using fallback {}", fallbackEmail);
                to = fallbackEmail;
            } else {
                log.warn("No recipient and no fallback configured — dropping email subject '{}'", subject);
                return;
            }
        }

        String html = buildHtmlBody(subject, bodyText, caseRef, status, priority);
        sendHtml(to, subject, html);
    }

    /**
     * Send an SLA / ops alert email — subject and HTML composed from primitives.
     */
    public void sendOperationalAlert(String to, String subject, String headline, String detail) {
        if (to == null || to.isBlank()) {
            if (fallbackEmail == null || fallbackEmail.isBlank()) {
                log.warn("Operational alert dropped — no recipient and no fallback. subject='{}'", subject);
                return;
            }
            to = fallbackEmail;
        }
        String html = buildHtmlBody(subject, detail, headline, null, null);
        sendHtml(to, subject, html);
    }

    private String buildHtmlBody(String subject, String body, String caseRef, String status, String priority) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"font-family:Arial,Helvetica,sans-serif;\">");
        sb.append("<h2 style=\"color:#0a3d62;\">").append(escape(subject)).append("</h2>");
        if (caseRef != null) {
            sb.append("<p><b>Case:</b> ").append(escape(caseRef)).append("</p>");
        }
        if (status != null) {
            sb.append("<p><b>Status:</b> ").append(escape(status)).append("</p>");
        }
        if (priority != null) {
            sb.append("<p><b>Priority:</b> ").append(escape(priority)).append("</p>");
        }
        if (body != null) {
            sb.append("<div style=\"margin-top:12px;\">")
              .append(escape(body).replace("\n", "<br/>"))
              .append("</div>");
        }
        sb.append("<hr/><small>Hokeka AML Platform — automated notification</small>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
