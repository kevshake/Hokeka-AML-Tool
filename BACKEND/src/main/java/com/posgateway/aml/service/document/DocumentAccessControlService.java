package com.posgateway.aml.service.document;

import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.entity.document.DocumentAccessLog;
import com.posgateway.aml.repository.MerchantDocumentRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.document.DocumentAccessLogRepository;
import com.posgateway.aml.service.cache.DocumentAccessCacheService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Document Access Control Service
 * Manages granular access controls for document access
 */
@Service
public class DocumentAccessControlService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAccessControlService.class);

    private final MerchantDocumentRepository documentRepository;
    private final DocumentAccessLogRepository accessLogRepository;
    private final DocumentAccessCacheService accessCacheService;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PspIsolationService pspIsolationService;
    private static final Set<String> DOCUMENT_ROLES = Set.of(
            "ADMIN",
            "AUDITOR",
            "CASE_MANAGER",
            "COMPLIANCE_OFFICER",
            "INVESTIGATOR",
            "MLRO",
            "PLATFORM_ADMIN",
            "PSP_ADMIN",
            "PSP_ANALYST",
            "PSP_USER",
            "SCREENING_ANALYST",
            "SUPER_ADMIN",
            "VIEWER");

    @Autowired
    public DocumentAccessControlService(
            MerchantDocumentRepository documentRepository,
            DocumentAccessLogRepository accessLogRepository,
            DocumentAccessCacheService accessCacheService,
            UserRepository userRepository,
            MerchantRepository merchantRepository,
            PspIsolationService pspIsolationService) {
        this.documentRepository = documentRepository;
        this.accessLogRepository = accessLogRepository;
        this.accessCacheService = accessCacheService;
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Check if user can access document
     * Uses Redis as an optimization after validating the persisted user/PSP scope.
     */
    public boolean canAccessDocument(Long documentId, Long userId, String userRole) {
        if (documentId == null || userId == null) return false;
        com.posgateway.aml.entity.User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        String persistedRole = user.getRole() != null ? user.getRole().getName() : "";
        if (userRole != null && !userRole.isBlank()
                && !persistedRole.equalsIgnoreCase(userRole)) {
            logger.warn("Ignored mismatched caller role for document access: userId={} supplied={} persisted={}",
                    userId, userRole, persistedRole);
        }

        Boolean cachedAccess =
                accessCacheService.getCachedAccessPermission(documentId, userId, persistedRole);
        if (cachedAccess != null) {
            logger.debug("Document access check from cache: docId={}, userId={}, role={}, access={}", 
                    documentId, userId, persistedRole, cachedAccess);
            return cachedAccess;
        }

        MerchantDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        com.posgateway.aml.entity.merchant.Merchant merchant =
                merchantRepository.findById(document.getMerchantId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Document merchant not found: " + document.getMerchantId()));

        boolean roleAllowed = DOCUMENT_ROLES.contains(persistedRole.toUpperCase());
        boolean tenantAllowed = pspIsolationService.isPlatformAdministrator(user)
                || (user.getPsp() != null
                    && merchant.getPsp() != null
                    && user.getPsp().getPspId().equals(merchant.getPsp().getPspId()));
        boolean hasAccess = roleAllowed && tenantAllowed;

        accessCacheService.cacheAccessPermission(
                documentId, userId, persistedRole, hasAccess);
        
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

