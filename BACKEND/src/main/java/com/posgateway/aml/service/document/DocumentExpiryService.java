package com.posgateway.aml.service.document;



import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// @RequiredArgsConstructor removed
@Service
public class DocumentExpiryService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DocumentExpiryService.class);

    private final MerchantDocumentRepository documentRepository;
    private final AlertRepository alertRepository;

    public DocumentExpiryService(MerchantDocumentRepository documentRepository, AlertRepository alertRepository) {
        this.documentRepository = documentRepository;
        this.alertRepository = alertRepository;
    }


    @Scheduled(cron = "${document.expiry.check.cron:0 0 4 * * *}") // Run daily at 4 AM
    @Transactional
    public void checkExpiringDocuments() {
        log.info("Starting scheduled document expiry check...");

        LocalDate thresholdDate = LocalDate.now().plusDays(30); // Check docs expiring in 30 days
        List<MerchantDocument> expiringDocs = documentRepository.findExpiringDocuments(thresholdDate);

        for (MerchantDocument doc : expiringDocs) {
            String alertReason = doc.getExpiryDate().isBefore(LocalDate.now())
                    ? "Document EXPIRED"
                    : "Document Expiring Soon";

            log.info("Document {} for merchant {} is expiring/expired on {}",
                    doc.getDocumentId(), doc.getMerchantId(), doc.getExpiryDate());

            createAlert(doc, alertReason);
        }

        log.info("Document expiry check complete. Processed {} documents.", expiringDocs.size());
    }

    private void createAlert(MerchantDocument doc, String reason) {
        Alert alert = new Alert();
        alert.setMerchantId(doc.getMerchantId());
        alert.setAction("DOCUMENT_EXPIRY_ALERT");
        alert.setReason(reason + ": " + doc.getDocumentType());
        alert.setStatus("open");
        alert.setSeverity("WARN"); // Default to WARN
        alert.setNotes("Document ID: " + doc.getDocumentId() + " Expiry: " + doc.getExpiryDate());

        alertRepository.save(alert);
    }
}
