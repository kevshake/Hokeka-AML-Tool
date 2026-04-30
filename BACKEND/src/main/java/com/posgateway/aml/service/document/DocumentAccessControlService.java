package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.entity.document.DocumentAccessLog;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import com.posgateway.aml.repository.document.DocumentAccessLogRepository;
import com.posgateway.aml.service.cache.DocumentAccessCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Document Access Control Service
 * Manages granular access controls for document access
 */
@Service
public class DocumentAccessControlService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAccessControlService.class);

    private final MerchantDocumentRepository documentRepository;
    private final DocumentAccessLogRepository accessLogRepository;
    private final DocumentAccessCacheService accessCacheService; // Aerospike cache for fast access checks

    @Autowired
    public DocumentAccessControlService(
            MerchantDocumentRepository documentRepository,
            DocumentAccessLogRepository accessLogRepository,
            DocumentAccessCacheService accessCacheService) {
        this.documentRepository = documentRepository;
        this.accessLogRepository = accessLogRepository;
        this.accessCacheService = accessCacheService;
    }

    /**
     * Check if user can access document
     * Uses Aerospike cache for fast authorization checks
     */
    public boolean canAccessDocument(Long documentId, Long userId, String userRole) {
        // Fast Aerospike cache lookup first
        Boolean cachedAccess = accessCacheService.getCachedAccessPermission(documentId, userId, userRole);
        if (cachedAccess != null) {
            logger.debug("Document access check from cache: docId={}, userId={}, role={}, access={}", 
                    documentId, userId, userRole, cachedAccess);
            return cachedAccess;
        }

        // Fallback to database check - verify document exists
        @SuppressWarnings("unused")
        MerchantDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        // Role-based access control
        boolean hasAccess;
        if ("ADMIN".equals(userRole) || "COMPLIANCE_OFFICER".equals(userRole)) {
            hasAccess = true; // Admins and compliance officers have full access
        } else {
            // Check if user is associated with the merchant
            // This would need to be enhanced based on actual user-merchant relationship
            // For now, allow access if user has appropriate role
            hasAccess = "PSP_ADMIN".equals(userRole) || "PSP_USER".equals(userRole);
        }

        // Cache the result in Aerospike for future fast lookups
        accessCacheService.cacheAccessPermission(documentId, userId, userRole, hasAccess);
        
        return hasAccess;
    }

    /**
     * Log document access
     */
    @Transactional
    public DocumentAccessLog logAccess(Long documentId, Long userId, String action, String ipAddress) {
        DocumentAccessLog accessLog = new DocumentAccessLog();
        accessLog.setDocumentId(documentId);
        accessLog.setUserId(userId);
        accessLog.setAction(action); // VIEW, DOWNLOAD, DELETE
        accessLog.setIpAddress(ipAddress);
        accessLog.setAccessedAt(LocalDateTime.now());

        logger.info("Document access logged: Document {} accessed by user {} - {}", 
                documentId, userId, action);
        return accessLogRepository.save(accessLog);
    }

    /**
     * Get access history for document
     */
    public List<DocumentAccessLog> getAccessHistory(Long documentId) {
        return accessLogRepository.findByDocumentIdOrderByAccessedAtDesc(documentId);
    }
}

