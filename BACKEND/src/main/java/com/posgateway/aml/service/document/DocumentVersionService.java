package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Document Version Service
 * Manages document versioning
 */
@Service
public class DocumentVersionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentVersionService.class);

    private final MerchantDocumentRepository documentRepository;

    @Autowired
    public DocumentVersionService(MerchantDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Create new version of document
     */
    @Transactional
    public MerchantDocument createNewVersion(Long documentId, MerchantDocument newVersion) {
        MerchantDocument current = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        // Mark current as not current version
        current.setIsCurrentVersion(false);
        documentRepository.save(current);

        // Create new version
        newVersion.setMerchantId(current.getMerchantId());
        newVersion.setDocumentType(current.getDocumentType());
        newVersion.setVersion(current.getVersion() + 1);
        newVersion.setPreviousVersionId(documentId);
        newVersion.setIsCurrentVersion(true);
        newVersion.setStatus("PENDING");

        logger.info("Created new version {} of document {}", newVersion.getVersion(), documentId);
        return documentRepository.save(newVersion);
    }

    /**
     * Get document version history
     */
    public List<MerchantDocument> getVersionHistory(Long merchantId, String documentType) {
        return documentRepository.findByMerchantId(merchantId).stream()
                .filter(doc -> documentType.equals(doc.getDocumentType()))
                .sorted((a, b) -> Integer.compare(
                        b.getVersion() != null ? b.getVersion() : 1,
                        a.getVersion() != null ? a.getVersion() : 1))
                .toList();
    }

    /**
     * Get current version of document
     */
    public Optional<MerchantDocument> getCurrentVersion(Long merchantId, String documentType) {
        return documentRepository.findByMerchantId(merchantId).stream()
                .filter(doc -> documentType.equals(doc.getDocumentType()))
                .filter(doc -> doc.getIsCurrentVersion() != null && doc.getIsCurrentVersion())
                .findFirst();
    }
}

