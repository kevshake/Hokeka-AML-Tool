package com.posgateway.aml.service;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the fix for W20-14: RoleService had zero cache eviction anywhere, unlike UserService
 * (which evicts the "users" cache — CustomUserDetailsService.loadUserByUsername's @Cacheable —
 * from every one of its own mutations). Editing a role's own permission set left every already-
 * cached user holding that role authorizing against stale (over-)permissions for up to the
 * cache's 5-minute TTL. Every mutating RoleService method must now carry a matching @CacheEvict.
 */
class RoleServiceCacheEvictionTest {

    private CacheEvict cacheEvictOn(String methodName, Class<?>... paramTypes) throws NoSuchMethodException {
        Method m = RoleService.class.getDeclaredMethod(methodName, paramTypes);
        CacheEvict evict = m.getAnnotation(CacheEvict.class);
        assertNotNull(evict, methodName + " is missing @CacheEvict — a role mutation here leaves "
                + "cached users authorizing against stale permissions for up to the cache TTL");
        return evict;
    }

    @Test
    void updatePermissionsEvictsTheUsersCache() throws Exception {
        CacheEvict evict = cacheEvictOn("updatePermissions", Long.class, java.util.Set.class);
        assertArrayEquals(new String[]{"users"}, evict.cacheNames());
        assertTrue(evict.allEntries());
    }

    @Test
    void updateRoleEvictsTheUsersCache() throws Exception {
        CacheEvict evict = cacheEvictOn("updateRole", Long.class, String.class, String.class);
        assertArrayEquals(new String[]{"users"}, evict.cacheNames());
        assertTrue(evict.allEntries());
    }

    @Test
    void deleteRoleEvictsTheUsersCache() throws Exception {
        CacheEvict evict = cacheEvictOn("deleteRole", Long.class);
        assertArrayEquals(new String[]{"users"}, evict.cacheNames());
        assertTrue(evict.allEntries());
    }
}
