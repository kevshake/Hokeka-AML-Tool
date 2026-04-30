package com.posgateway.aml.service.sanctions;

import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import com.posgateway.aml.service.case_management.CaseCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Periodic Sanctions Screening Service
 * 
 * Runs a scheduled batch job to screen all eligible merchants and their UBOs
 * against the sanctions list (cached in Aerospike).
 * 
 * Frequency: Configurable (default nightly or twice daily)
 * Triggers: Automatically creates Cases via CaseCreationService on hits.
 */
@Service
public class PeriodicSanctionsScreeningService {

    private static final Logger logger = LoggerFactory.getLogger(PeriodicSanctionsScreeningService.class);

    private final MerchantRepository merchantRepository;
    private final AerospikeSanctionsScreeningService screeningService;
    private final CaseCreationService caseCreationService;

    @Autowired
    public PeriodicSanctionsScreeningService(MerchantRepository merchantRepository,
            AerospikeSanctionsScreeningService screeningService,
            CaseCreationService caseCreationService) {
        this.merchantRepository = merchantRepository;
        this.screeningService = screeningService;
        this.caseCreationService = caseCreationService;
    }

    /**
     * Scheduled Job: Screen merchants due for review.
     * Runs daily at 3 AM (after sanctions download at 2 AM).
     */
    @Scheduled(cron = "${sanctions.screening.cron:0 0 3 * * *}")
    public void performScheduledScreening() {
        logger.info("Starting periodic sanctions screening batch...");

        LocalDate today = LocalDate.now();
        List<Merchant> merchants = merchantRepository.findMerchantsNeedingRescreening(today);

        logger.info("Found {} merchants due for screening", merchants.size());

        for (Merchant merchant : merchants) {
            try {
                processMerchant(merchant);
            } catch (Exception e) {
                logger.error("Failed to screen merchant {}: {}", merchant.getLegalName(), e.getMessage());
            }
        }

        logger.info("Periodic sanctions screening complete.");
    }

    @Transactional
    public void processMerchant(Merchant merchant) {
        boolean alertTriggered = false;

        // 1. Screen Merchant Entity (Legal & Trading Name)
        ScreeningResult merchantResult = screeningService.screenMerchant(merchant.getLegalName(),
                merchant.getTradingName());

        if (merchantResult.hasMatches()) {
            logger.warn("SANCTIONS HIT: Merchant {} matched {}", merchant.getLegalName(),
                    merchantResult.getHighestMatchScore());

            // Trigger Case
            // Note: We need a placeholder TransactionEntity or modify trigger to accept
            // Merchant directly
            // For now, we'll create a dummy TX wrapper or assume trigger accepts partial
            // Adapting CaseCreationService to support Entity-based triggers would be best,
            // but assuming we pass the merchant ID securely is key.
            // Using a dedicated method in CaseCreationService would be cleaner.
            // For this implementation, invoking a new method we'll add to
            // CaseCreationService: triggerCaseForEntity()

            Long pspId = (merchant.getPsp() != null) ? merchant.getPsp().getPspId() : null;

            caseCreationService.triggerCaseFromSanctions(
                    merchant.getMerchantId(),
                    pspId,
                    "SANCTIONS_WATCHLIST",
                    "Periodic Screening Hit: " + merchant.getLegalName() + " Score: "
                            + merchantResult.getHighestMatchScore());
            alertTriggered = true;
        }

        // 2. Screen Beneficial Owners
        for (BeneficialOwner ubo : merchant.getBeneficialOwners()) {
            ScreeningResult uboResult = screeningService.screenBeneficialOwner(ubo.getFullName(), ubo.getDateOfBirth());

            if (uboResult.hasMatches()) {
                logger.warn("SANCTIONS HIT: UBO {} matched {}", ubo.getFullName(), uboResult.getHighestMatchScore());

                Long pspId = (merchant.getPsp() != null) ? merchant.getPsp().getPspId() : null;

                caseCreationService.triggerCaseFromSanctions(
                        merchant.getMerchantId(),
                        pspId,
                        "PEP_SANCTIONS_UBO",
                        "UBO Hit: " + ubo.getFullName() + " linked to " + merchant.getLegalName());
                ubo.setIsSanctioned(true);
                alertTriggered = true;
            }
        }

        // 3. Update Merchant Screening Status
        merchant.setLastScreenedAt(java.time.LocalDateTime.now());
        // Set next due date (default 7 days, or 1 day if we want frequent)
        // User asked for "twice as much", assuming frequent. Let's make it configurable
        // or stick to entity logic.
        merchant.updateNextScreeningDue();

        if (alertTriggered) {
            merchant.setRiskLevel("CRITICAL"); // Auto-escalate risk
        }

        merchantRepository.save(merchant);
    }
}
