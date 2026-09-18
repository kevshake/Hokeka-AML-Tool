package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

/**
 * Document Retention Service
 * Enforces document retention policy and auto-deletes expired documents
 */
@Service
public class DocumentRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentRetentionService.class);

    private final MerchantDocumentRepository documentRepository;

    @Value("${document.retention.years:7}")
    private int retentionYears;

    @Value("${document.retention.enabled:true}")
    private boolean retentionEnabled;

    @Autowired
    public DocumentRetentionService(MerchantDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Scheduled task to enforce retention policy
     */
    @Scheduled(cron = "${document.retention.cron:0 0 3 * * *}") // Daily at 3 AM
    @Transactional
    public void enforceRetentionPolicy() {
        if (!retentionEnabled) {
            logger.info("Document retention enforcement is disabled");
            return;
        }

        logger.info("Starting document retention enforcement...");

        LocalDate retentionDate = LocalDate.now().minusYears(retentionYears);
        List<MerchantDocument> expiredDocuments = documentRepository.findByExpiryDateBefore(retentionDate);

        int deleted = 0;
        for (MerchantDocument doc : expiredDocuments) {
            try {
                // Only delete if not current version and expired
                if (doc.getIsCurrentVersion() == null || !doc.getIsCurrentVersion()) {
                    // Actually delete file from storage
                    if (doc.getFilePath() != null) {
                        try {
                            Files.deleteIfExists(Paths.get(doc.getFilePath()));
                        } catch (IOException e) {
                            logger.error("Failed to delete physical file: {}", doc.getFilePath(), e);
                        }
                    }
                    documentRepository.delete(doc);
                    deleted++;
                }
            } catch (Exception e) {
                logger.error("Error deleting expired document {}: {}", doc.getDocumentId(), e.getMessage());
            }
        }

        logger.info("Document retention enforcement complete: {} documents deleted", deleted);
    }

    /**
     * Check if document should be retained
     */
    public boolean shouldRetain(MerchantDocument document) {
        if (document.getExpiryDate() == null) {
            return true; // No expiry date, retain
        }

        LocalDate retentionDate = LocalDate.now().minusYears(retentionYears);
        return document.getExpiryDate().isAfter(retentionDate);
    }

    /**
     * Get documents scheduled for deletion
     */
    public List<MerchantDocument> getDocumentsScheduledForDeletion() {
        LocalDate retentionDate = LocalDate.now().minusYears(retentionYears);
        return documentRepository.findByExpiryDateBefore(retentionDate).stream()
                .filter(doc -> doc.getIsCurrentVersion() == null || !doc.getIsCurrentVersion())
                .toList();
    }
}
