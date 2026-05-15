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
        Long pspId = (merchant.getPsp() != null) ? merchant.getPsp().getPspId() : null;

        // 1. Screen Merchant Entity (Legal & Trading Name)
        ScreeningResult merchantResult = screeningService.screenMerchant(merchant.getLegalName(),
                merchant.getTradingName());

        if (merchantResult.hasMatches()) {
            logger.warn("SANCTIONS HIT: Merchant {} matched {}", merchant.getLegalName(),
                    merchantResult.getHighestMatchScore());

            String matchDetails = "Periodic Screening Hit: " + merchant.getLegalName()
                    + " Score: " + merchantResult.getHighestMatchScore();
            caseCreationService.triggerCaseFromSanctionsForMerchant(
                    merchant.getMerchantId(),
                    pspId,
                    matchDetails,
                    "SANCTIONS_WATCHLIST");
            alertTriggered = true;
        }

        // 2. Screen Beneficial Owners
        for (BeneficialOwner ubo : merchant.getBeneficialOwners()) {
            ScreeningResult uboResult = screeningService.screenBeneficialOwner(ubo.getFullName(), ubo.getDateOfBirth());

            if (uboResult.hasMatches()) {
                logger.warn("SANCTIONS HIT: UBO {} matched {}", ubo.getFullName(), uboResult.getHighestMatchScore());

                String uboDetails = "UBO Hit: " + ubo.getFullName()
                        + " linked to " + merchant.getLegalName()
                        + " Score: " + uboResult.getHighestMatchScore();
                caseCreationService.triggerCaseFromSanctionsForMerchant(
                        merchant.getMerchantId(),
                        pspId,
                        uboDetails,
                        "PEP_SANCTIONS_UBO");
                ubo.setIsSanctioned(true);
                alertTriggered = true;
            }
        }

        // 3. Update Merchant Screening Status
        merchant.setLastScreenedAt(java.time.LocalDateTime.now());
        merchant.updateNextScreeningDue();

        if (alertTriggered) {
            merchant.setRiskLevel("CRITICAL"); // Auto-escalate risk
        }

        merchantRepository.save(merchant);
    }
}
