package com.posgateway.aml.service.marketing;

import com.posgateway.aml.dto.marketing.WebsiteContactRequest;
import com.posgateway.aml.dto.marketing.WebsiteContactResponse;
import com.posgateway.aml.entity.marketing.WebsiteContactInquiry;
import com.posgateway.aml.repository.marketing.WebsiteContactInquiryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Persists and acknowledges public marketing-site contact / demo submissions.
 */
@Service
public class WebsiteContactService {

    private static final Logger log = LoggerFactory.getLogger(WebsiteContactService.class);

    private final WebsiteContactInquiryRepository repository;

    public WebsiteContactService(WebsiteContactInquiryRepository repository) {
        this.repository = repository;
    }

    /**
     * Validate, normalise, and store an inquiry; returns a stable acknowledgement payload.
     */
    @Transactional
    public WebsiteContactResponse submit(WebsiteContactRequest request, String clientIp, String userAgent) {
        WebsiteContactInquiry row = new WebsiteContactInquiry();
        row.setFullName(normaliseName(request.getFullName()));
        row.setWorkEmail(normaliseEmail(request.getWorkEmail()));
        row.setOrganization(trimToNull(request.getOrganization()));
        row.setPhone(trimToNull(request.getPhone()));
        row.setInquiryType(normaliseInquiryType(request.getInquiryType()));
        row.setMessage(request.getMessage().trim());
        row.setSource("marketing-site");
        row.setClientIpHash(hashIp(clientIp));
        row.setUserAgent(truncate(userAgent, 500));
        row.setStatus("NEW");

        WebsiteContactInquiry saved = repository.save(row);
        log.info("Marketing site inquiry {} stored for {}", saved.getId(), saved.getWorkEmail());

        return new WebsiteContactResponse(saved.getId(), saved.getStatus(), saved.getCreatedAt());
    }

    /** Collapse whitespace and title-case the submitter name for consistent CRM export. */
    private static String normaliseName(String raw) {
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed;
    }

    /** Lower-case email for deduplication while keeping the local-part intact. */
    private static String normaliseEmail(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Default unknown inquiry types to DEMO. */
    private static String normaliseInquiryType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "DEMO";
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "DEMO", "PARTNERSHIP", "SUPPORT", "INTEGRATION" -> upper;
            default -> "DEMO";
        };
    }

    /** Return null when optional text fields are blank after trim. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** SHA-256 hex digest of client IP for abuse tracing without storing raw IPs. */
    private static String hashIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(clientIp.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /** Hard-cap optional metadata strings to column width. */
    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }
}
