package com.posgateway.aml.service.edd;

import com.posgateway.aml.entity.edd.EddEvidenceEvent;
import com.posgateway.aml.entity.edd.EnhancedDueDiligenceRequest;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.edd.EddEvidenceEventRepository;
import com.posgateway.aml.repository.edd.EnhancedDueDiligenceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class EnhancedDueDiligenceService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(EnhancedDueDiligenceService.class);

    private final MerchantRepository merchantRepository;
    private final EnhancedDueDiligenceRequestRepository eddRepository;
    private final EddEvidenceEventRepository eventRepository;

    public EnhancedDueDiligenceService(MerchantRepository merchantRepository,
                                       EnhancedDueDiligenceRequestRepository eddRepository,
                                       EddEvidenceEventRepository eventRepository) {
        this.merchantRepository = merchantRepository;
        this.eddRepository = eddRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Auto-initiate EDD if it has not already been started for this merchant. Idempotent:
     * if an EDD request already exists and is past NOT_STARTED, this is a no-op so a repeated
     * underwriting run does not reset in-flight EDD progress. Used by the underwriting
     * orchestrator when the decision is ENHANCED_DUE_DILIGENCE.
     */
    @Transactional
    public void ensureEddInitiated(Long merchantId, String actor) {
        java.util.Optional<EnhancedDueDiligenceRequest> existing = eddRepository.findByMerchantId(merchantId);
        if (existing.isPresent()) {
            String status = existing.get().getStatus();
            if (status != null && !"NOT_STARTED".equalsIgnoreCase(status)) {
                return;
            }
        }
        initiateEdd(merchantId, false, actor, "Auto-initiated by underwriting (EDD outcome)");
    }

    @Transactional
    public EddStatus initiateEdd(Long merchantId, boolean siteVisitRequired, String actor, String notes) {
        merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));
        EnhancedDueDiligenceRequest req = eddRepository.findByMerchantId(merchantId)
                .orElseGet(() -> new EnhancedDueDiligenceRequest(merchantId));
        req.setStatus("IN_PROGRESS");
        req.setSourceOfFundsVerified(false);
        req.setSourceOfWealthVerified(false);
        req.setSiteVisitRequired(siteVisitRequired);
        req.setSiteVisitCompleted(false);
        req.setSeniorManagementApproval(false);
        req.setFamilyAssociateChecks(false);
        req.setTransactionPurposeReview(false);
        req.setInitiatedAt(LocalDateTime.now());
        req.setInitiatedBy(actor);
        req.setLastUpdatedAt(LocalDateTime.now());
        req.setLastUpdatedBy(actor);
        req.setCompletedAt(null);
        req.setCompletedBy(null);
        req = eddRepository.save(req);
        appendEvent(req, "INITIATED", null, true, actor, notes);
        log.info("EDD initiated for merchant {} by {}", merchantId, actor);
        return toView(req, events(req));
    }

    @Transactional
    public EddStatus updateItemStatus(Long merchantId, String itemCode, boolean completed,
                                      String actor, String notes) {
        EnhancedDueDiligenceRequest req = eddRepository.findByMerchantId(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("EDD not started for merchant: " + merchantId));
        String normalizedCode = itemCode == null ? "" : itemCode.strip().toUpperCase(Locale.ROOT);
        boolean previous = itemValue(req, normalizedCode);
        if (previous && !completed && (notes == null || notes.isBlank())) {
            throw new IllegalArgumentException("A reason is required when revoking EDD evidence");
        }
        setItemValue(req, normalizedCode, completed);
        req.setLastUpdatedAt(LocalDateTime.now());
        req.setLastUpdatedBy(actor);
        checkCompletion(req, actor);
        req = eddRepository.save(req);
        appendEvent(req, normalizedCode, previous, completed, actor, notes);
        return toView(req, events(req));
    }

    @Transactional(readOnly = true)
    public EddStatus getEddStatus(Long merchantId) {
        return eddRepository.findByMerchantId(merchantId)
                .map(req -> toView(req, events(req)))
                .orElseGet(() -> EddStatus.notStarted(merchantId));
    }

    private boolean itemValue(EnhancedDueDiligenceRequest req, String code) {
        return switch (code) {
            case "SOURCE_OF_FUNDS", "SOF" -> req.isSourceOfFundsVerified();
            case "SOURCE_OF_WEALTH", "SOW" -> req.isSourceOfWealthVerified();
            case "SITE_VISIT", "VISIT" -> req.isSiteVisitCompleted();
            case "SENIOR_MANAGEMENT_APPROVAL", "SENIOR_APPROVAL" -> req.isSeniorManagementApproval();
            case "FAMILY_ASSOCIATE_CHECKS", "FAMILY_CHECK" -> req.isFamilyAssociateChecks();
            case "TRANSACTION_PURPOSE_REVIEW", "PURPOSE_REVIEW" -> req.isTransactionPurposeReview();
            default -> throw new IllegalArgumentException("Unsupported EDD item code: " + code);
        };
    }

    private void setItemValue(EnhancedDueDiligenceRequest req, String code, boolean value) {
        switch (code) {
            case "SOURCE_OF_FUNDS", "SOF" -> req.setSourceOfFundsVerified(value);
            case "SOURCE_OF_WEALTH", "SOW" -> req.setSourceOfWealthVerified(value);
            case "SITE_VISIT", "VISIT" -> req.setSiteVisitCompleted(value);
            case "SENIOR_MANAGEMENT_APPROVAL", "SENIOR_APPROVAL" -> req.setSeniorManagementApproval(value);
            case "FAMILY_ASSOCIATE_CHECKS", "FAMILY_CHECK" -> req.setFamilyAssociateChecks(value);
            case "TRANSACTION_PURPOSE_REVIEW", "PURPOSE_REVIEW" -> req.setTransactionPurposeReview(value);
            default -> throw new IllegalArgumentException("Unsupported EDD item code: " + code);
        }
    }

    private void checkCompletion(EnhancedDueDiligenceRequest req, String actor) {
        boolean completed = req.isSourceOfFundsVerified()
                && req.isSourceOfWealthVerified()
                && (!req.isSiteVisitRequired() || req.isSiteVisitCompleted())
                && req.isSeniorManagementApproval()
                && req.isFamilyAssociateChecks()
                && req.isTransactionPurposeReview();
        if (completed) {
            req.setStatus("COMPLETED");
            req.setCompletedAt(LocalDateTime.now());
            req.setCompletedBy(actor);
            Merchant merchant = merchantRepository.findById(req.getMerchantId())
                    .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + req.getMerchantId()));
            merchant.setLastEddReviewAt(req.getCompletedAt());
            merchantRepository.save(merchant);
        } else {
            req.setStatus("IN_PROGRESS");
            req.setCompletedAt(null);
            req.setCompletedBy(null);
        }
    }

    private void appendEvent(EnhancedDueDiligenceRequest req, String itemCode, Boolean previous,
                             boolean value, String actor, String notes) {
        EddEvidenceEvent event = new EddEvidenceEvent();
        event.setEddRequestId(req.getId());
        event.setMerchantId(req.getMerchantId());
        event.setItemCode(itemCode);
        event.setPreviousValue(previous);
        event.setNewValue(value);
        event.setPerformedBy(actor);
        event.setNotes(notes == null || notes.isBlank() ? null : notes.strip());
        event.setOccurredAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    private List<EvidenceEvent> events(EnhancedDueDiligenceRequest req) {
        return eventRepository.findByEddRequestIdOrderByOccurredAtDesc(req.getId()).stream()
                .map(event -> new EvidenceEvent(event.getId(), event.getItemCode(), event.getPreviousValue(),
                        event.getNewValue(), event.getPerformedBy(), event.getNotes(), event.getOccurredAt()))
                .toList();
    }

    private static EddStatus toView(EnhancedDueDiligenceRequest req, List<EvidenceEvent> events) {
        return new EddStatus(req.getId(), req.getMerchantId(), req.getStatus(), req.isSourceOfFundsVerified(),
                req.isSourceOfWealthVerified(), req.isSiteVisitRequired(), req.isSiteVisitCompleted(),
                req.isSeniorManagementApproval(), req.isFamilyAssociateChecks(), req.isTransactionPurposeReview(),
                req.getInitiatedAt(), req.getInitiatedBy(), req.getLastUpdatedAt(), req.getLastUpdatedBy(),
                req.getCompletedAt(), req.getCompletedBy(), events);
    }

    public record EddStatus(Long id, Long merchantId, String status, boolean sourceOfFundsVerified,
                            boolean sourceOfWealthVerified, boolean siteVisitRequired, boolean siteVisitCompleted,
                            boolean seniorManagementApproval, boolean familyAssociateChecks,
                            boolean transactionPurposeReview, LocalDateTime initiatedAt, String initiatedBy,
                            LocalDateTime lastUpdatedAt, String lastUpdatedBy, LocalDateTime completedAt,
                            String completedBy, List<EvidenceEvent> events) {
        static EddStatus notStarted(Long merchantId) {
            return new EddStatus(null, merchantId, "NOT_STARTED", false, false, false, false, false, false,
                    false, null, null, null, null, null, null, List.of());
        }
    }

    public record EvidenceEvent(Long id, String itemCode, Boolean previousValue, boolean newValue,
                                String performedBy, String notes, LocalDateTime occurredAt) {}
}
