package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Service to enforce Role-Based Access Control (RBAC) rules.
 * Determines if a user can View or Act on a case.
 */
@Service("casePermissionService")
public class CasePermissionService {

    private final ComplianceCaseRepository caseRepository;

    @Autowired
    public CasePermissionService(ComplianceCaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    /**
     * Check if a user can VIEW a case.
     * Rule:
     * - Admins/Officers/Auditors/MLRO: Can view ALL cases.
     * - Analysts: Can view cases assigned to them (or unassigned in their queue -
     * simplified to all for now as queues are shared).
     */
    public boolean canView(Long caseId, User user) {
        if (isAdminOrOfficer(user)) {
            return true;
        }

        // Fetch case to check assignment if needed
        return caseRepository.findById(caseId)
                .map(kase -> canViewCase(kase, user))
                .orElse(false);
    }

    private boolean canViewCase(ComplianceCase kase, User user) {
        UserRole role = UserRole.valueOf(user.getRole().getName());

        // 1. PSP Data Isolation
        if (role == UserRole.PSP_ADMIN || role == UserRole.PSP_ANALYST) {
            // Can only view cases belonging to their PSP
            if (user.getPsp() == null) {
                return false; // Misconfiguration: PSP user without PSP ID
            }
            return user.getPsp().getPspId().equals(kase.getPspId());
        }

        // 2. Bank/Platform wide roles
        // Admins, Officers, Bank Auditors, MLRO can view all.
        // Standard Analysts can view all (to pick from queue), unless restricted by
        // queues later.
        return true;
    }

    /**
     * Check if a user can PERFORM AN ACTION on a case.
     */
    public boolean canAct(Long caseId, User user, String actionType) {
        return caseRepository.findById(caseId)
                .map(kase -> {
                    // First check visibility
                    if (!canViewCase(kase, user))
                        return false;
                    return canActOnCase(kase, user, actionType);
                })
                .orElse(false);
    }

    private boolean canActOnCase(ComplianceCase kase, User user, String actionType) {
        UserRole role = UserRole.valueOf(user.getRole().getName());

        // 1. Admin/MLRO Override
        if (role == UserRole.ADMIN || role == UserRole.MLRO) {
            return true;
        }

        // 2. Closed Case Rules
        if (isClosed(kase)) {
            // Only Admin/MLRO can reopen/act on closed cases
            return false;
        }

        // 3. Officer/Senior Analyst Actions (Approve/Reject/SAR)
        if (Set.of("APPROVE", "REJECT", "FILE_SAR", "FILE_STR").contains(actionType)) {
            return role == UserRole.COMPLIANCE_OFFICER ||
                    role == UserRole.MLRO ||
                    role == UserRole.BANK_OFFICER ||
                    role == UserRole.SENIOR_ANALYST;
        }

        // 4. View-Only Roles
        if (role == UserRole.AUDITOR || role == UserRole.BANK_AUDITOR || role == UserRole.VIEWER) {
            return false;
        }

        // 5. Assignment Rules for Analysts (Standard & PSP)
        if (actionType.equals("ASSIGN")) {
            return true;
        }

        // For other actions (Note, Evidence), must be assigned
        if (kase.getAssignedTo() != null && !kase.getAssignedTo().getId().equals(user.getId())) {
            // Managers/Officers can override
            return role == UserRole.COMPLIANCE_OFFICER ||
                    role == UserRole.CASE_MANAGER ||
                    role == UserRole.BANK_OFFICER ||
                    role == UserRole.SENIOR_ANALYST ||
                    role == UserRole.PSP_ADMIN;
        }

        return true;
    }

    private boolean isAdminOrOfficer(User user) {
        try {
            UserRole role = UserRole.valueOf(user.getRole().getName());
            return role == UserRole.ADMIN ||
                    role == UserRole.MLRO ||
                    role == UserRole.COMPLIANCE_OFFICER ||
                    role == UserRole.AUDITOR ||
                    role == UserRole.BANK_OFFICER ||
                    role == UserRole.BANK_AUDITOR;
            // PSP_ADMIN is explicitly removed from here to enforce canViewCase isolation
            // logic
            // role == UserRole.PSP_ADMIN;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isClosed(ComplianceCase kase) {
        String s = kase.getStatus().name();
        return s.startsWith("CLOSED") || s.equals("SAR_FILED") || s.equals("RESOLVED");
    }
}
