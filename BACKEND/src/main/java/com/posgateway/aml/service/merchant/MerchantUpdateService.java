package com.posgateway.aml.service.merchant;

import com.posgateway.aml.dto.request.MerchantUpdateRequest;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.underwriting.MerchantVerificationSignal;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.underwriting.MerchantVerificationSignalRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import com.posgateway.aml.service.security.PspIsolationService;
import com.posgateway.aml.service.underwriting.MerchantVerificationOrchestrator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// @RequiredArgsConstructor removed
@Service
public class MerchantUpdateService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MerchantUpdateService.class);

    private final MerchantRepository merchantRepository;
    private final AmlScreeningOrchestrator screeningOrchestrator;
    private final AuditTrailRepository auditTrailRepository;
    private final PspIsolationService pspIsolationService;
    private final MerchantVerificationOrchestrator verificationOrchestrator;
    private final MerchantVerificationSignalRepository verificationSignalRepository;

    public MerchantUpdateService(MerchantRepository merchantRepository, AmlScreeningOrchestrator screeningOrchestrator,
            AuditTrailRepository auditTrailRepository, PspIsolationService pspIsolationService,
            MerchantVerificationOrchestrator verificationOrchestrator,
            MerchantVerificationSignalRepository verificationSignalRepository) {
        this.merchantRepository = merchantRepository;
        this.screeningOrchestrator = screeningOrchestrator;
        this.auditTrailRepository = auditTrailRepository;
        this.pspIsolationService = pspIsolationService;
        this.verificationOrchestrator = verificationOrchestrator;
        this.verificationSignalRepository = verificationSignalRepository;
    }

    @Transactional
    public Merchant updateMerchant(Long merchantId, MerchantUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        pspIsolationService.validateMerchantAccess(merchant);

        boolean sensitiveChange = false;
        boolean settlementChanged = false;
        Map<String, Object> changes = new HashMap<>();

        // Check and update fields
        if (request.getLegalName() != null && !request.getLegalName().equals(merchant.getLegalName())) {
            changes.put("legalName", "From: " + merchant.getLegalName() + " To: " + request.getLegalName());
            merchant.setLegalName(request.getLegalName());
            sensitiveChange = true;
        }

        if (request.getTradingName() != null && !request.getTradingName().equals(merchant.getTradingName())) {
            changes.put("tradingName", "From: " + merchant.getTradingName() + " To: " + request.getTradingName());
            merchant.setTradingName(request.getTradingName());
            sensitiveChange = true;
        }

        if (request.getAddressCountry() != null && !request.getAddressCountry().equals(merchant.getAddressCountry())) {
            changes.put("country", "From: " + merchant.getAddressCountry() + " To: " + request.getAddressCountry());
            merchant.setAddressCountry(request.getAddressCountry());
            sensitiveChange = true;
        }

        // Update other non-sensitive fields
        if (request.getWebsite() != null && !java.util.Objects.equals(request.getWebsite(), merchant.getWebsite())) {
            changes.put("website", "UPDATED");
            merchant.setWebsite(request.getWebsite());
        }
        if (request.getContactEmail() != null
                && !java.util.Objects.equals(request.getContactEmail(), merchant.getContactEmail())) {
            changes.put("contactEmail", "UPDATED");
            merchant.setContactEmail(request.getContactEmail());
        }
        if (request.getCbkSettlementAccountNumber() != null
                && !java.util.Objects.equals(
                        request.getCbkSettlementAccountNumber(), merchant.getCbkSettlementAccountNumber())) {
            changes.put("cbkSettlementAccountConfigured", true);
            merchant.setCbkSettlementAccountNumber(request.getCbkSettlementAccountNumber());
            settlementChanged = true;
        }
        if (request.getCbkEconomicSectorCode() != null
                && !java.util.Objects.equals(
                        request.getCbkEconomicSectorCode(), merchant.getCbkEconomicSectorCode())) {
            changes.put("cbkEconomicSectorCode",
                    "From: " + merchant.getCbkEconomicSectorCode()
                            + " To: " + request.getCbkEconomicSectorCode());
            merchant.setCbkEconomicSectorCode(request.getCbkEconomicSectorCode());
        }
        if (request.getExpectedMonthlyVolume() != null
                && !java.util.Objects.equals(
                        request.getExpectedMonthlyVolume(), merchant.getExpectedMonthlyVolume())) {
            changes.put("expectedMonthlyVolume",
                    "From: " + merchant.getExpectedMonthlyVolume()
                            + " To: " + request.getExpectedMonthlyVolume());
            merchant.setExpectedMonthlyVolume(request.getExpectedMonthlyVolume());
        }

        merchant.setUpdatedAt(LocalDateTime.now());
        Merchant savedMerchant = merchantRepository.save(merchant);

        // Audit Log
        createAuditTrail(merchantId, changes);

        // Settlement-account-change control: a new settlement account is a major bust-out
        // signal. Record an explicit high-severity verification signal (forcing manual
        // review) so the change is visible in the underwriting evidence trail and the
        // account is treated as unverified until re-checked.
        if (settlementChanged) {
            log.warn("Settlement account changed for merchant {} — recording change control + forcing re-verification",
                    merchantId);
            try {
                MerchantVerificationSignal sig = new MerchantVerificationSignal();
                sig.setMerchantId(merchantId);
                sig.setRunId("settlement-change-" + System.currentTimeMillis());
                sig.setSignalCode("SETTLEMENT_ACCOUNT_CHANGED");
                sig.setSeverity("HIGH");
                sig.setSource("INTERNAL");
                sig.setConfidence(1.0);
                sig.setRequiresManualReview(true);
                sig.setDetail("Settlement account changed post-onboarding — re-verify account ownership "
                        + "(name match) and review for bust-out before releasing settlements.");
                sig.setObservedAt(LocalDateTime.now());
                verificationSignalRepository.save(sig);
            } catch (Exception e) {
                log.error("Failed to record settlement-change signal for merchant {}: {}",
                        merchantId, e.getMessage());
            }
        }

        // Change-triggered re-KYB: a material change (identity/country/settlement) forces a
        // fresh underwriting verification, a re-screen, and resets the screening clock so the
        // periodic job also re-checks. CDD is kept current rather than drifting from reality.
        boolean materialChange = sensitiveChange || settlementChanged;
        if (materialChange) {
            log.info("Material change on merchant {} — re-verifying (KYB) and re-screening", merchantId);
            savedMerchant.setNextScreeningDue(LocalDate.now());
            merchantRepository.save(savedMerchant);
            try {
                verificationOrchestrator.verify(savedMerchant);
            } catch (Exception e) {
                log.error("Re-verification failed for merchant {}: {}", merchantId, e.getMessage());
            }
            screeningOrchestrator.screenMerchant(savedMerchant);
        }

        return savedMerchant;
    }

    private void createAuditTrail(Long merchantId, Map<String, Object> changes) {
        if (changes.isEmpty())
            return;

        AuditTrail audit = AuditTrail.builder()
                .merchantId(merchantId)
                .action("UPDATE")
                .performedBy(currentActor())
                .decision("UPDATED")
                .decisionReason("Merchant details updated")
                .evidence(changes)
                .build();

        auditTrailRepository.save(audit);
    }

    @Transactional
    public void deleteMerchant(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        pspIsolationService.validateMerchantAccess(merchant);
        merchantRepository.delete(merchant);

        // Audit log for deletion
        AuditTrail audit = AuditTrail.builder()
                .merchantId(merchantId)
                .action("DELETE")
                .performedBy(currentActor())
                .decision("DELETED")
                .decisionReason("Merchant deleted by user")
                .build();
        auditTrailRepository.save(audit);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                ? authentication.getName()
                : "SYSTEM";
    }
}
