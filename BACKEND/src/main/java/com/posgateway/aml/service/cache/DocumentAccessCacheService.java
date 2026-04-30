package com.posgateway.aml.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Document Access Cache Service
 * Caches document access permissions in Aerospike for fast authorization checks
 */
@Service
public class DocumentAccessCacheService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAccessCacheService.class);

    @Autowired
    private AerospikeCacheService cacheService;

    @Value("${document.access.cache.ttl.hours:1}")
    private int cacheTtlHours;

    private static final String SET_DOCUMENT_ACCESS = "document_access";
    private static final String SET_DOCUMENT_PERMISSIONS = "document_permissions";

    /**
     * Cache document access permission
     */
    public void cacheAccessPermission(Long documentId, Long userId, String role, boolean hasAccess) {
        String key = documentId + ":" + userId + ":" + role;
        cacheService.put(SET_DOCUMENT_ACCESS, key, hasAccess, (int) TimeUnit.HOURS.toSeconds(cacheTtlHours));
        logger.debug("Cached document access permission: docId={}, userId={}, role={}, access={}", 
                documentId, userId, role, hasAccess);
    }

    /**
     * Check cached access permission (fast lookup)
     */
    public Boolean getCachedAccessPermission(Long documentId, Long userId, String role) {
        String key = documentId + ":" + userId + ":" + role;
        return cacheService.get(SET_DOCUMENT_ACCESS, key, Boolean.class);
    }

    /**
     * Cache document permissions map
     */
    public void cacheDocumentPermissions(Long documentId, java.util.Map<String, Boolean> permissions) {
        String key = String.valueOf(documentId);
        cacheService.putMap(SET_DOCUMENT_PERMISSIONS, key, 
                new java.util.HashMap<>(permissions), 
                (int) TimeUnit.HOURS.toSeconds(cacheTtlHours));
        logger.debug("Cached document permissions map: docId={}, {} permissions", documentId, permissions.size());
    }

    /**
     * Get cached document permissions map
     */
    public java.util.Map<String, Object> getCachedDocumentPermissions(Long documentId) {
        String key = String.valueOf(documentId);
        return cacheService.getMap(SET_DOCUMENT_PERMISSIONS, key);
    }

    /**
     * Invalidate document access cache
     */
    public void invalidateDocumentAccess(Long documentId) {
        // Note: Aerospike doesn't support wildcard deletes efficiently
        // In production, maintain a list of keys or use a different strategy
        logger.debug("Document access cache invalidation requested for docId={}", documentId);
    }

    /**
     * Invalidate user's document access cache
     */
    public void invalidateUserAccess(Long userId) {
        logger.debug("User document access cache invalidation requested for userId={}", userId);
    }
}

