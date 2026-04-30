package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.Permission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Permission Service
 * Handles granular permission checking.
 * Now works with dynamic Role entity.
 */
@Service
public class PermissionService {

    /**
     * Check if current user has a specific permission
     */
    public boolean hasPermission(Permission permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // Assume security is disabled if no auth is present
            return true;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof User) {
            return hasPermission(((User) principal).getRole(), permission);
        }
        return true; // Default to allow if principal is string or other type
    }

    /**
     * Check if a role has a specific permission
     */
    public boolean hasPermission(Role role, Permission permission) {
        if (role == null) {
            return false;
        }

        // ADMIN always has all permissions
        if ("ADMIN".equalsIgnoreCase(role.getName())) {
            return true;
        }

        return role.getPermissions() != null && role.getPermissions().contains(permission);
    }
}
