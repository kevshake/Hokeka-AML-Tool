package com.posgateway.aml.service.monitoring;



import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.case_management.ComplianceCaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

// @RequiredArgsConstructor removed
@Service
public class ContentMonitoringService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ContentMonitoringService.class);

    private final MerchantRepository merchantRepository;
    private final ComplianceCaseService caseService;

    public ContentMonitoringService(MerchantRepository merchantRepository, ComplianceCaseService caseService) {
        this.merchantRepository = merchantRepository;
        this.caseService = caseService;
    }

    @SuppressWarnings("unused")
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${g2.monitoring.enabled:true}")
    private boolean monitoringEnabled;

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
        try {
            // In a real scenario, use a specific scraping service or G2 API.
            // Here we simulate a check or simple HTML fetch.
            // String htmlContent = restTemplate.getForObject(merchant.getWebsite(),
            // String.class);

            // Mocking the check for reliability in local env without real URLs
            String htmlContent = "<html><body>Welcome to our shop</body></html>";

            for (String keyword : RISKY_KEYWORDS) {
                if (htmlContent.toLowerCase().contains(keyword)) {
                    log.warn("RISK DETECTED: Merchant {} website contains keyword '{}'", merchant.getLegalName(),
                            keyword);

                    // Trigger compliance case (legacy service now minimal)
                    caseService.createCase("G2 Monitoring found risky keyword: " + keyword + " for merchant " + merchant.getLegalName());
                    break; // One hit is enough to flag
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan website for merchant {}: {}", merchant.getLegalName(), e.getMessage());
        }
    }
}
