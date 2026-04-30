package com.posgateway.aml.service.security;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.merchant.Merchant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * PSP Isolation Service
 * 
 * Centralized service to enforce PSP/Bank data isolation.
 * Ensures that PSP users can only access data belonging to their PSP.
 * Platform Administrators can access all PSPs' data.
 * 
 * This service should be used by all controllers and services to enforce
 * multi-tenant data isolation.
 */
@Service
public class PspIsolationService {

    private static final Logger logger = LoggerFactory.getLogger(PspIsolationService.class);

    /**
     * Get the current authenticated user's PSP ID.
     * Returns 0 for Platform Administrators (Super Admin PSP).
     * Returns >0 for PSP users.
     * All users must have a PSP ID for consistency.
     * 
     * @return PSP ID of current user (0 for Super Admin, >0 for PSP users)
     */
    public Long getCurrentUserPspId() {
        User user = getCurrentUser();
        if (user == null) {
            return 0L; // Default to Super Admin PSP ID
        }
        
        // All users must have a PSP assigned (PSP ID 0 for Super Admin)
        if (user.getPsp() == null) {
            logger.warn("User {} has no PSP assigned - defaulting to Super Admin PSP ID 0", user.getUsername());
            return 0L; // Default to Super Admin PSP ID
        }
        
        return user.getPsp().getPspId();
    }

    /**
     * Get the current authenticated user's PSP code.
     * Returns null for Platform Administrators.
     * 
     * @return PSP code of current user, or null if Platform Admin
     */
    public String getCurrentUserPspCode() {
        User user = getCurrentUser();
        if (user == null || user.getPsp() == null) {
            return null;
        }
        return user.getPsp().getPspCode();
    }

    /**
     * Check if current user is a Platform Administrator.
     * Platform Administrators can access all PSPs' data.
     * 
     * @return true if Platform Admin, false otherwise
     */
    public boolean isPlatformAdministrator() {
        User user = getCurrentUser();
        return user != null && isPlatformAdministrator(user);
    }

    /**
     * Check if a user is a Platform Administrator.
     * Platform Administrators have PSP ID 0 (Super Admin PSP).
     * 
     * @param user User to check
     * @return true if Platform Admin, false otherwise
     */
    public boolean isPlatformAdministrator(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        
        String roleName = user.getRole().getName();
        
        // Platform Administrators have these roles and PSP ID 0 (Super Admin PSP)
        boolean hasAdminRole = roleName.equals("ADMIN") || 
                roleName.equals("MLRO") || 
                roleName.equals("PLATFORM_ADMIN") ||
                roleName.equals("APP_CONTROLLER");
        
        if (!hasAdminRole) {
            return false;
        }
        
        // Check if user has PSP ID 0 (Super Admin PSP)
        if (user.getPsp() == null) {
            return false; // Should not happen after migration, but handle gracefully
        }
        
        return user.getPsp().getPspId() != null && user.getPsp().getPspId() == 0L;
    }

    /**
     * Check if current user is a PSP user (PSP_ADMIN, PSP_ANALYST, etc.).
     * PSP users can only access their own PSP's data.
     * 
     * @return true if PSP user, false otherwise
     */
    public boolean isPspUser() {
        User user = getCurrentUser();
        return user != null && isPspUser(user);
    }

    /**
     * Check if a user is a PSP user.
     * 
     * @param user User to check
     * @return true if PSP user, false otherwise
     */
    public boolean isPspUser(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        
        String roleName = user.getRole().getName();
        return roleName.equals("PSP_ADMIN") || 
               roleName.equals("PSP_ANALYST") ||
               roleName.equals("PSP_USER") ||
               (user.getPsp() != null && !isPlatformAdministrator(user));
    }

    /**
     * Validate that a PSP user can access data belonging to the specified PSP ID.
     * Platform Administrators (PSP ID 0) can always access any PSP.
     * 
     * @param targetPspId PSP ID of the data being accessed (must not be null)
     * @throws SecurityException if PSP user tries to access another PSP's data
     */
    public void validatePspAccess(Long targetPspId) {
        User user = getCurrentUser();
        if (user == null) {
            throw new SecurityException("User not authenticated");
        }

        // Platform Administrators (PSP ID 0) can access all PSPs
        if (isPlatformAdministrator(user)) {
            return;
        }

        // PSP users can only access their own PSP
        Long userPspId = getCurrentUserPspId();
        if (userPspId == null || userPspId == 0L) {
            throw new SecurityException("PSP user has invalid PSP ID - misconfiguration");
        }
        
        if (targetPspId == null) {
            throw new SecurityException("Target PSP ID cannot be null");
        }
        
        if (!userPspId.equals(targetPspId)) {
            logger.warn("PSP user {} attempted to access PSP {} data (their PSP: {})", 
                user.getUsername(), targetPspId, userPspId);
            throw new SecurityException("Cannot access data from another PSP");
        }
    }

    /**
     * Validate that a PSP user can access a case.
     * 
     * @param caseEntity Case entity to check
     * @throws SecurityException if PSP user tries to access another PSP's case
     */
    public void validateCaseAccess(ComplianceCase caseEntity) {
        if (caseEntity == null) {
            throw new IllegalArgumentException("Case cannot be null");
        }
        validatePspAccess(caseEntity.getPspId());
    }

    /**
     * Validate that a PSP user can access a transaction.
     * 
     * @param transaction Transaction entity to check
     * @throws SecurityException if PSP user tries to access another PSP's transaction
     */
    public void validateTransactionAccess(TransactionEntity transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        validatePspAccess(transaction.getPspId());
    }

    /**
     * Validate that a PSP user can access a merchant.
     * 
     * @param merchant Merchant entity to check
     * @throws SecurityException if PSP user tries to access another PSP's merchant
     */
    public void validateMerchantAccess(Merchant merchant) {
        if (merchant == null) {
            throw new IllegalArgumentException("Merchant cannot be null");
        }
        if (merchant.getPsp() == null) {
            throw new SecurityException("Merchant has no PSP assigned");
        }
        validatePspAccess(merchant.getPsp().getPspId());
    }

    /**
     * Sanitize PSP ID parameter from request.
     * PSP users cannot override their PSP ID - it's automatically set from their user context.
     * Platform Administrators (PSP ID 0) can specify any PSP ID or null for all PSPs.
     * 
     * @param requestedPspId PSP ID from request parameter (may be null)
     * @return Sanitized PSP ID - user's PSP ID for PSP users, requested ID for Platform Admins
     * @throws SecurityException if PSP user tries to access another PSP's data
     */
    public Long sanitizePspId(Long requestedPspId) {
        User user = getCurrentUser();
        if (user == null) {
            throw new SecurityException("User not authenticated");
        }

        // Platform Administrators (PSP ID 0) can specify any PSP ID
        if (isPlatformAdministrator(user)) {
            return requestedPspId; // Can be null for "all PSPs" or specific PSP ID
        }

        // PSP users cannot override their PSP ID
        Long userPspId = getCurrentUserPspId();
        if (userPspId == null || userPspId == 0L) {
            throw new SecurityException("PSP user has invalid PSP ID - misconfiguration");
        }

        // If PSP user specified a PSP ID, validate it matches their PSP
        if (requestedPspId != null && !userPspId.equals(requestedPspId)) {
            logger.warn("PSP user {} attempted to access PSP {} data (their PSP: {})", 
                user.getUsername(), requestedPspId, userPspId);
            throw new SecurityException("Cannot access data from another PSP");
        }

        // Return user's PSP ID (ignore requested PSP ID)
        return userPspId;
    }

    /**
     * Get current authenticated user.
     * 
     * @return Current user or null if not authenticated
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        
        if (auth.getPrincipal() instanceof User) {
            return (User) auth.getPrincipal();
        }
        
        return null;
    }
}
