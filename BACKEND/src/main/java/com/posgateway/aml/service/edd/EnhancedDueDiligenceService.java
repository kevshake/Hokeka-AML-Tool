package com.posgateway.aml.service.edd;

import com.posgateway.aml.entity.edd.EnhancedDueDiligenceRequest;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.edd.EnhancedDueDiligenceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EnhancedDueDiligenceService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(EnhancedDueDiligenceService.class);

    @SuppressWarnings("unused")
    private final MerchantRepository merchantRepository;
    private final EnhancedDueDiligenceRequestRepository eddRepository;

    public EnhancedDueDiligenceService(MerchantRepository merchantRepository,
                                       EnhancedDueDiligenceRequestRepository eddRepository) {
        this.merchantRepository = merchantRepository;
        this.eddRepository = eddRepository;
    }

    @Transactional
    public void initiateEdd(Long merchantId) {
        log.info("Initiating Enhanced Due Diligence for Merchant {}", merchantId);
        EnhancedDueDiligenceRequest req = eddRepository.findByMerchantId(merchantId)
                .orElseGet(() -> new EnhancedDueDiligenceRequest(merchantId));
        // Reset verification state on (re-)initiation; preserves the legacy in-memory
        // semantic where a fresh EddStatus was always put() into the map.
        req.setStatus("IN_PROGRESS");
        req.setSourceOfFundsVerified(false);
        req.setSourceOfWealthVerified(false);
        req.setSiteVisitCompleted(false);
        req.setSeniorManagementApproval(false);
        req.setFamilyAssociateChecks(false);
        req.setTransactionPurposeReview(false);
        req.setInitiatedAt(LocalDateTime.now());
        req.setCompletedAt(null);
        eddRepository.save(req);
    }

    @Transactional
    public void updateDocumentStatus(Long merchantId, String docType, boolean verified) {
        EnhancedDueDiligenceRequest req = eddRepository.findByMerchantId(merchantId).orElse(null);
        if (req == null) {
            return;
        }
        switch (docType) {
            case "SOF"             -> req.setSourceOfFundsVerified(verified);
            case "SOW"             -> req.setSourceOfWealthVerified(verified);
            case "VISIT"           -> req.setSiteVisitCompleted(verified);
            case "SENIOR_APPROVAL" -> req.setSeniorManagementApproval(verified);
            case "FAMILY_CHECK"    -> req.setFamilyAssociateChecks(verified);
            case "PURPOSE_REVIEW"  -> req.setTransactionPurposeReview(verified);
            default -> { /* unknown doc type — ignore as before */ }
        }

        log.info("Updated EDD status for Merchant {}: {} = {}", merchantId, docType, verified);
        checkCompletion(req);
        eddRepository.save(req);
    }

    @Transactional(readOnly = true)
    public EddStatus getEddStatus(Long merchantId) {
        return eddRepository.findByMerchantId(merchantId)
                .map(EnhancedDueDiligenceService::toView)
                .orElseGet(EddStatus::new);
    }

    private void checkCompletion(EnhancedDueDiligenceRequest req) {
        boolean basicEdd = req.isSourceOfFundsVerified() && req.isSourceOfWealthVerified();
        boolean kenyaEdd = req.isSeniorManagementApproval()
                && req.isFamilyAssociateChecks()
                && req.isTransactionPurposeReview();

        if (basicEdd && kenyaEdd) {
            log.info("EDD Completed for Merchant {}", req.getMerchantId());
            req.setStatus("COMPLETED");
            req.setCompletedAt(LocalDateTime.now());
            // Could trigger workflow to unblock merchant
        }
    }

    private static EddStatus toView(EnhancedDueDiligenceRequest req) {
        EddStatus s = new EddStatus();
        s.setMerchantId(req.getMerchantId());
        s.setSourceOfFundsVerified(req.isSourceOfFundsVerified());
        s.setSourceOfWealthVerified(req.isSourceOfWealthVerified());
        s.setSiteVisitCompleted(req.isSiteVisitCompleted());
        s.setSeniorManagementApproval(req.isSeniorManagementApproval());
        s.setFamilyAssociateChecks(req.isFamilyAssociateChecks());
        s.setTransactionPurposeReview(req.isTransactionPurposeReview());
        return s;
    }

    /**
     * Read-only view DTO retained for legacy callers that consumed
     * {@code EnhancedDueDiligenceService.EddStatus} from the in-memory shape.
     */
    public static class EddStatus {
        private Long merchantId;
        private boolean sourceOfFundsVerified;
        private boolean sourceOfWealthVerified;
        private boolean siteVisitCompleted;
        private boolean seniorManagementApproval;
        private boolean familyAssociateChecks;
        private boolean transactionPurposeReview;

        public EddStatus() {
        }

        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long v) { this.merchantId = v; }

        public boolean isSourceOfFundsVerified() { return sourceOfFundsVerified; }
        public void setSourceOfFundsVerified(boolean v) { this.sourceOfFundsVerified = v; }

        public boolean isSourceOfWealthVerified() { return sourceOfWealthVerified; }
        public void setSourceOfWealthVerified(boolean v) { this.sourceOfWealthVerified = v; }

        public boolean isSiteVisitCompleted() { return siteVisitCompleted; }
        public void setSiteVisitCompleted(boolean v) { this.siteVisitCompleted = v; }

        public boolean isSeniorManagementApproval() { return seniorManagementApproval; }
        public void setSeniorManagementApproval(boolean v) { this.seniorManagementApproval = v; }

        public boolean isFamilyAssociateChecks() { return familyAssociateChecks; }
        public void setFamilyAssociateChecks(boolean v) { this.familyAssociateChecks = v; }

        public boolean isTransactionPurposeReview() { return transactionPurposeReview; }
        public void setTransactionPurposeReview(boolean v) { this.transactionPurposeReview = v; }
    }
}
