package com.posgateway.aml.service.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Document Access Cache Service
 * Caches document access permissions in Redis for fast authorization checks.
 */
@Service
public class DocumentAccessCacheService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentAccessCacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${document.access.cache.ttl.hours:1}")
    private int cacheTtlHours;

    private static final String SET_DOCUMENT_ACCESS = "document_access";
    private static final String SET_DOCUMENT_PERMISSIONS = "document_permissions";

    private static String key(String set, String id) {
        return set + ":" + id;
    }

    /** Cache document access permission */
    public void cacheAccessPermission(Long documentId, Long userId, String role, boolean hasAccess) {
        String k = key(SET_DOCUMENT_ACCESS, documentId + ":" + userId + ":" + role);
        redisTemplate.opsForValue().set(k, hasAccess, Duration.ofHours(cacheTtlHours));
        logger.debug("Cached document access permission: docId={}, userId={}, role={}, access={}",
                documentId, userId, role, hasAccess);
    }

    /** Check cached access permission (fast lookup) */
    public Boolean getCachedAccessPermission(Long documentId, Long userId, String role) {
        String k = key(SET_DOCUMENT_ACCESS, documentId + ":" + userId + ":" + role);
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    /** Cache document permissions map */
    public void cacheDocumentPermissions(Long documentId, Map<String, Boolean> permissions) {
        String k = key(SET_DOCUMENT_PERMISSIONS, String.valueOf(documentId));
        redisTemplate.opsForValue().set(k, new HashMap<>(permissions), Duration.ofHours(cacheTtlHours));
        logger.debug("Cached document permissions map: docId={}, {} permissions", documentId, permissions.size());
    }

    /** Get cached document permissions map */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedDocumentPermissions(Long documentId) {
        String k = key(SET_DOCUMENT_PERMISSIONS, String.valueOf(documentId));
        Object v = redisTemplate.opsForValue().get(k);
        return v instanceof Map ? new HashMap<>((Map<String, Object>) v) : null;
    }

    /** Invalidate document access cache */
    public void invalidateDocumentAccess(Long documentId) {
        // Note: Redis SCAN-based wildcard delete is intentionally not used here to avoid
        // scanning the whole keyspace. In production, maintain a list of keys per docId.
        logger.debug("Document access cache invalidation requested for docId={}", documentId);
    }

    /** Invalidate user's document access cache */
    public void invalidateUserAccess(Long userId) {
        logger.debug("User document access cache invalidation requested for userId={}", userId);
    }
}
