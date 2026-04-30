package com.posgateway.aml.service.kyc;

import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import com.posgateway.aml.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * KYC Expiration Tracking Service
 * Tracks and alerts on expiring KYC documents
 */
@Service
public class KycExpirationTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(KycExpirationTrackingService.class);

    private final MerchantDocumentRepository documentRepository;
    private final MerchantRepository merchantRepository;

    @Value("${kyc.expiration.alert-days:30}")
    private int alertDaysBeforeExpiration;

    @Value("${kyc.expiration.critical-days:7}")
    private int criticalDaysBeforeExpiration;

    @Autowired
    public KycExpirationTrackingService(
            MerchantDocumentRepository documentRepository,
            MerchantRepository merchantRepository) {
        this.documentRepository = documentRepository;
        this.merchantRepository = merchantRepository;
    }

    /**
     * Get expiring documents
     */
    public List<ExpiringDocument> getExpiringDocuments(int daysAhead) {
        LocalDate expirationDate = LocalDate.now().plusDays(daysAhead);
        List<MerchantDocument> documents = documentRepository.findByExpiryDateBetween(
                LocalDate.now(), expirationDate);

        return documents.stream()
                .map(doc -> {
                    ExpiringDocument expiring = new ExpiringDocument();
                    expiring.setDocumentId(doc.getDocumentId());
                    expiring.setMerchantId(doc.getMerchantId());
                    expiring.setDocumentType(doc.getDocumentType());
                    expiring.setExpiryDate(doc.getExpiryDate());
                    expiring.setDaysUntilExpiration((int) ChronoUnit.DAYS.between(LocalDate.now(), doc.getExpiryDate()));
                    expiring.setStatus(getExpirationStatus(doc.getExpiryDate()));
                    return expiring;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get documents requiring immediate attention
     */
    public List<ExpiringDocument> getCriticalExpiringDocuments() {
        return getExpiringDocuments(criticalDaysBeforeExpiration);
    }

    /**
     * Get documents expiring soon (within alert window)
     */
    public List<ExpiringDocument> getExpiringSoonDocuments() {
        return getExpiringDocuments(alertDaysBeforeExpiration);
    }

    /**
     * Check expiration status for a merchant
     */
    public MerchantExpirationStatus checkMerchantExpirationStatus(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));

        List<MerchantDocument> documents = documentRepository.findByMerchantId(merchantId);
        List<ExpiringDocument> expiringDocs = documents.stream()
                .filter(doc -> doc.getExpiryDate() != null)
                .map(doc -> {
                    ExpiringDocument expiring = new ExpiringDocument();
                    expiring.setDocumentId(doc.getDocumentId());
                    expiring.setMerchantId(doc.getMerchantId());
                    expiring.setDocumentType(doc.getDocumentType());
                    expiring.setExpiryDate(doc.getExpiryDate());
                    expiring.setDaysUntilExpiration((int) ChronoUnit.DAYS.between(LocalDate.now(), doc.getExpiryDate()));
                    expiring.setStatus(getExpirationStatus(doc.getExpiryDate()));
                    return expiring;
                })
                .filter(doc -> doc.getDaysUntilExpiration() <= alertDaysBeforeExpiration)
                .collect(Collectors.toList());

        MerchantExpirationStatus status = new MerchantExpirationStatus();
        status.setMerchantId(merchantId);
        status.setMerchantName(merchant.getLegalName());
        status.setExpiringDocuments(expiringDocs);
        status.setHasExpiredDocuments(expiringDocs.stream().anyMatch(doc -> doc.getDaysUntilExpiration() < 0));
        status.setHasCriticalExpiring(expiringDocs.stream().anyMatch(doc -> doc.getStatus().equals("CRITICAL")));
        status.setRequiresAction(expiringDocs.size() > 0);

        return status;
    }

    /**
     * Scheduled task to check for expiring documents
     */
    @Scheduled(cron = "${kyc.expiration.check-cron:0 0 9 * * *}") // Daily at 9 AM
    @Transactional
    public void checkExpiringDocuments() {
        logger.info("Checking for expiring KYC documents...");

        List<ExpiringDocument> expiring = getExpiringSoonDocuments();
        List<ExpiringDocument> critical = getCriticalExpiringDocuments();

        logger.info("Found {} documents expiring within {} days, {} critical", 
                expiring.size(), alertDaysBeforeExpiration, critical.size());

        // Create alerts for critical expiring documents
        for (ExpiringDocument doc : critical) {
            createExpirationAlert(doc);
        }
    }

    /**
     * Create alert for expiring document
     */
    private void createExpirationAlert(ExpiringDocument doc) {
        // TODO: Integrate with Alert service
        logger.warn("KYC Document expiring: Merchant {}, Document Type {}, Expires in {} days",
                doc.getMerchantId(), doc.getDocumentType(), doc.getDaysUntilExpiration());
    }

    /**
     * Get expiration status
     */
    private String getExpirationStatus(LocalDate expiryDate) {
        if (expiryDate.isBefore(LocalDate.now())) {
            return "EXPIRED";
        }
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        if (daysUntil <= criticalDaysBeforeExpiration) {
            return "CRITICAL";
        }
        if (daysUntil <= alertDaysBeforeExpiration) {
            return "WARNING";
        }
        return "OK";
    }

    /**
     * Expiring Document DTO
     */
    public static class ExpiringDocument {
        private Long documentId;
        private Long merchantId;
        private String documentType;
        private LocalDate expiryDate;
        private int daysUntilExpiration;
        private String status;

        // Getters and Setters
        public Long getDocumentId() { return documentId; }
        public void setDocumentId(Long documentId) { this.documentId = documentId; }
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
        public int getDaysUntilExpiration() { return daysUntilExpiration; }
        public void setDaysUntilExpiration(int daysUntilExpiration) { this.daysUntilExpiration = daysUntilExpiration; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * Merchant Expiration Status DTO
     */
    public static class MerchantExpirationStatus {
        private Long merchantId;
        private String merchantName;
        private List<ExpiringDocument> expiringDocuments;
        private boolean hasExpiredDocuments;
        private boolean hasCriticalExpiring;
        private boolean requiresAction;

        // Getters and Setters
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public List<ExpiringDocument> getExpiringDocuments() { return expiringDocuments; }
        public void setExpiringDocuments(List<ExpiringDocument> expiringDocuments) { this.expiringDocuments = expiringDocuments; }
        public boolean isHasExpiredDocuments() { return hasExpiredDocuments; }
        public void setHasExpiredDocuments(boolean hasExpiredDocuments) { this.hasExpiredDocuments = hasExpiredDocuments; }
        public boolean isHasCriticalExpiring() { return hasCriticalExpiring; }
        public void setHasCriticalExpiring(boolean hasCriticalExpiring) { this.hasCriticalExpiring = hasCriticalExpiring; }
        public boolean isRequiresAction() { return requiresAction; }
        public void setRequiresAction(boolean requiresAction) { this.requiresAction = requiresAction; }
    }
}

