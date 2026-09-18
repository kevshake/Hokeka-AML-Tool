package com.posgateway.aml.service.reporting;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class ReportDeliveryService {
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String mailHost;
    private final String fromAddress;

    public ReportDeliveryService(@Autowired(required = false) JavaMailSender mailSender,
                                 @Value("${notifications.email-enabled:false}") boolean enabled,
                                 @Value("${spring.mail.host:}") String mailHost,
                                 @Value("${notifications.from-address:no-reply@hokeka.com}") String fromAddress) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.mailHost = mailHost;
        this.fromAddress = fromAddress;
    }

    public void deliver(List<String> recipients, String subject, String body, String filePath) {
        if (recipients == null || recipients.isEmpty()) return;
        if (!enabled || mailSender == null || mailHost.isBlank() || fromAddress.isBlank()) {
            throw new IllegalStateException("Scheduled report email delivery is not configured");
        }
        File attachment = new File(filePath);
        if (!attachment.isFile()) throw new IllegalStateException("Scheduled report attachment is unavailable");
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients.toArray(String[]::new));
            helper.setSubject(subject == null || subject.isBlank() ? "Hokeka scheduled compliance report" : subject);
            helper.setText(body == null || body.isBlank()
                    ? "Your scheduled compliance report is attached." : body, false);
            helper.addAttachment(attachment.getName(), attachment);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Scheduled report email delivery failed: " + e.getMessage(), e);
        }
    }
}
