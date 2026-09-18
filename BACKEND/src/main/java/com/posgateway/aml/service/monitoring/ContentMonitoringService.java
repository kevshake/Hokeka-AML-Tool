package com.posgateway.aml.service.monitoring;



import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.case_management.ComplianceCaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
public class ContentMonitoringService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContentMonitoringService.class);
    private static final String UA = "HokekaAML-G2/1.0 (+https://hokeka.com/bot)";

    private final MerchantRepository merchantRepository;
    private final ComplianceCaseService caseService;
    private final RestTemplate restTemplate;

    public ContentMonitoringService(MerchantRepository merchantRepository,
                                    ComplianceCaseService caseService,
                                    RestTemplateBuilder restTemplateBuilder) {
        this.merchantRepository = merchantRepository;
        this.caseService = caseService;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .defaultHeader(HttpHeaders.USER_AGENT, UA)
                .build();
    }

    @Value("${g2.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${g2.monitoring.max-bytes:524288}")
    private int maxBytes;

    // Keywords that suggest high risk or undeclared business lines
    private static final List<String> RISKY_KEYWORDS = Arrays.asList(
            "gambling", "casino", "betting",
            "crypto", "bitcoin", "wallet",
            "pharmacy", "drugs", "prescription",
            "adult", "xxx");

    @Scheduled(cron = "0 0 4 * * *") // Daily at 4 AM
    public void performContentMonitoring() {
        if (!monitoringEnabled)
            return;

        log.info("Starting G2 Content Monitoring Scan...");
        List<Merchant> activeMerchants = merchantRepository.findMerchantsNeedingRescreening(java.time.LocalDate.now());

        for (Merchant merchant : activeMerchants) {
            if (merchant.getWebsite() != null && !merchant.getWebsite().isEmpty()) {
                scanMerchantWebsite(merchant);
            }
        }
    }

    public boolean isEnabled() {
        return monitoringEnabled;
    }

    /**
     * Scan one merchant website for transaction-laundering / undeclared-business keywords.
     * Creates a compliance case when a risky keyword is found.
     */
    public G2ScanResult scanMerchantWebsite(Merchant merchant) {
        if (!monitoringEnabled) {
            return G2ScanResult.disabled(merchant.getMerchantId(), merchant.getWebsite());
        }
        if (merchant.getWebsite() == null || merchant.getWebsite().isBlank()) {
            return G2ScanResult.noWebsite(merchant.getMerchantId());
        }
        try {
            String url = normalizeWebsiteUrl(merchant.getWebsite());
            String htmlContent = restTemplate.getForObject(url, String.class);
            if (htmlContent == null || htmlContent.isBlank()) {
                log.debug("No website content returned for merchant {}", merchant.getLegalName());
                return G2ScanResult.clear(merchant.getMerchantId(), url, "No content returned");
            }

            String lower = htmlContent.toLowerCase();
            for (String keyword : RISKY_KEYWORDS) {
                if (lower.contains(keyword)) {
                    log.warn("RISK DETECTED: Merchant {} website {} contains keyword '{}'",
                            merchant.getLegalName(), url, keyword);
                    caseService.createCase("G2 Monitoring found risky keyword '" + keyword
                            + "' on " + url + " for merchant " + merchant.getLegalName());
                    return G2ScanResult.match(merchant.getMerchantId(), url, keyword);
                }
            }
            return G2ScanResult.clear(merchant.getMerchantId(), url, "No risky keywords detected");
        } catch (URISyntaxException | IllegalArgumentException e) {
            log.warn("Skipping invalid website URL for merchant {}: {}", merchant.getLegalName(), merchant.getWebsite());
            return G2ScanResult.error(merchant.getMerchantId(), merchant.getWebsite(), "Invalid website URL");
        } catch (RestClientException e) {
            log.warn("Failed to scan website for merchant {}: {}", merchant.getLegalName(), e.getMessage());
            return G2ScanResult.error(merchant.getMerchantId(), merchant.getWebsite(), e.getMessage());
        }
    }

    public record G2ScanResult(
            Long merchantId,
            String website,
            String scannedUrl,
            String status,
            String matchedKeyword,
            String message,
            boolean caseCreated) {
        static G2ScanResult disabled(Long merchantId, String website) {
            return new G2ScanResult(merchantId, website, null, "DISABLED", null,
                    "G2 content monitoring is disabled (G2_MONITORING_ENABLED=false)", false);
        }

        static G2ScanResult noWebsite(Long merchantId) {
            return new G2ScanResult(merchantId, null, null, "NO_WEBSITE", null,
                    "Merchant has no website URL configured", false);
        }

        static G2ScanResult clear(Long merchantId, String scannedUrl, String message) {
            return new G2ScanResult(merchantId, scannedUrl, scannedUrl, "CLEAR", null, message, false);
        }

        static G2ScanResult match(Long merchantId, String scannedUrl, String keyword) {
            return new G2ScanResult(merchantId, scannedUrl, scannedUrl, "MATCH", keyword,
                    "Risky keyword detected — compliance case opened", true);
        }

        static G2ScanResult error(Long merchantId, String website, String message) {
            return new G2ScanResult(merchantId, website, null, "ERROR", null, message, false);
        }
    }

    private String normalizeWebsiteUrl(String website) throws URISyntaxException {
        String trimmed = website.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        URI uri = new URI(trimmed);
        if (uri.getHost() == null) {
            throw new URISyntaxException(trimmed, "Website URL must include a host");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new URISyntaxException(trimmed, "Only HTTP and HTTPS website URLs are supported");
        }
        return uri.toString();
    }
}
