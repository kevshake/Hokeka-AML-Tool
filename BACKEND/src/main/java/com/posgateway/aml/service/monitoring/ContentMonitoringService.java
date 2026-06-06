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
import org.springframework.web.client.RestTemplate;

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
        List<Merchant> activeMerchants = merchantRepository.findMerchantsNeedingRescreening(java.time.LocalDate.now()); // Reuse
                                                                                                                        // logic
                                                                                                                        // or
                                                                                                                        // findActive

        for (Merchant merchant : activeMerchants) {
            if (merchant.getWebsite() != null && !merchant.getWebsite().isEmpty()) {
                checkMerchantWebsite(merchant);
            }
        }
    }

    private void checkMerchantWebsite(Merchant merchant) {
        String url = normalizeUrl(merchant.getWebsite());
        if (url == null) return;
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (resp.getStatusCode().isError() || resp.getBody() == null) {
                log.debug("G2 scan: {} returned status={} for merchant {}",
                        url, resp.getStatusCode(), merchant.getLegalName());
                return;
            }
            String body = resp.getBody();
            if (body.length() > maxBytes) body = body.substring(0, maxBytes);
            String lower = body.toLowerCase();
            for (String keyword : RISKY_KEYWORDS) {
                if (lower.contains(keyword)) {
                    log.warn("RISK DETECTED: Merchant {} website {} contains keyword '{}'",
                            merchant.getLegalName(), url, keyword);
                    caseService.createCase("G2 Monitoring found risky keyword '" + keyword
                            + "' on " + url + " for merchant " + merchant.getLegalName());
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("G2 scan failed for merchant {} ({}): {}",
                    merchant.getLegalName(), url, e.getMessage());
        }
    }

    private static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }
}
