package com.posgateway.aml.service.merchant;

import com.posgateway.aml.dto.request.MerchantUpdateRequest;
import com.posgateway.aml.entity.compliance.AuditTrail;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.AuditTrailRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.aml.AmlScreeningOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

// @RequiredArgsConstructor removed
@Service
public class MerchantUpdateService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MerchantUpdateService.class);

    private final MerchantRepository merchantRepository;
    private final AmlScreeningOrchestrator screeningOrchestrator;
    private final AuditTrailRepository auditTrailRepository;

    public MerchantUpdateService(MerchantRepository merchantRepository, AmlScreeningOrchestrator screeningOrchestrator,
            AuditTrailRepository auditTrailRepository) {
        this.merchantRepository = merchantRepository;
        this.screeningOrchestrator = screeningOrchestrator;
        this.auditTrailRepository = auditTrailRepository;
    }

    @Transactional
    public Merchant updateMerchant(Long merchantId, MerchantUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        boolean sensitiveChange = false;
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
        if (request.getWebsite() != null)
            merchant.setWebsite(request.getWebsite());
        if (request.getContactEmail() != null)
            merchant.setContactEmail(request.getContactEmail());
        if (request.getExpectedMonthlyVolume() != null)
            merchant.setExpectedMonthlyVolume(request.getExpectedMonthlyVolume());

        merchant.setUpdatedAt(java.time.LocalDateTime.now());
        Merchant savedMerchant = merchantRepository.save(merchant);

        // Audit Log
        createAuditTrail(merchantId, changes);

        // Trigger Re-screening if sensitive
        if (sensitiveChange) {
            log.info("Sensitive changes detected for merchant {}, triggering re-screening", merchantId);
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
                .performedBy("SYSTEM") // or user from context
                .decision("UPDATED")
                .decisionReason("Merchant details updated")
                .evidence(changes)
                .build();

        auditTrailRepository.save(audit);
    }

    @Transactional
    public void deleteMerchant(Long merchantId) {
        if (!merchantRepository.existsById(merchantId)) {
            throw new IllegalArgumentException("Merchant not found: " + merchantId);
        }
        merchantRepository.deleteById(merchantId);

        // Audit log for deletion
        AuditTrail audit = AuditTrail.builder()
                .merchantId(merchantId)
                .action("DELETE")
                .performedBy("SYSTEM")
                .decision("DELETED")
                .decisionReason("Merchant deleted by user")
                .build();
        auditTrailRepository.save(audit);
    }
}
