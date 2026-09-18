package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

// @RequiredArgsConstructor removed
@Service
@SuppressWarnings("null") // Repository methods return Optional, saved entities are non-null
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<Role> getRolesForPsp(Psp psp) {
        return roleRepository.findByPspIsNullOrPsp(psp);
    }

    public List<Role> getSystemRoles() {
        return roleRepository.findByPspIsNull();
    }

    @Transactional
    public Role createRole(String name, String description, Psp psp, Set<Permission> permissions) {
        // Check uniqueness
        if (psp == null) {
            if (roleRepository.findByNameAndPspIsNull(name).isPresent()) {
                throw new IllegalArgumentException("System role with this name already exists");
            }
        } else {
            if (roleRepository.findByNameAndPsp(name, psp).isPresent()) {
                throw new IllegalArgumentException("Role with this name already exists for this PSP");
            }
        }

        Role role = Role.builder()
                .name(name)
                .description(description)
                .psp(psp)
                .permissions(permissions)
                .build();

        return roleRepository.save(role);
    }

    /**
     * W20-14 fix: RoleService had zero cache eviction anywhere, unlike UserService (which evicts
     * "users" — the CustomUserDetailsService.loadUserByUsername cache — from every one of its own
     * mutations). Editing a role's own permission set here left every already-cached user holding
     * that role authorizing against their OLD permissions for up to the cache's 5-minute TTL — a
     * genuine security-relevant staleness window (e.g. revoking MANAGE_USERS from a compromised
     * role took up to 5 minutes to actually take effect for logged-in holders of that role).
     * allEntries=true rather than a per-user key: the cache is keyed by username/email, not role
     * id, and a role can be held by many users, so there's no single key to evict precisely.
     */
    @Transactional
    @CacheEvict(cacheNames = "users", allEntries = true)
    public Role updatePermissions(Long roleId, Set<Permission> permissions) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    @Transactional
    @CacheEvict(cacheNames = "users", allEntries = true)
    public Role updateRole(Long roleId, String name, String description) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (name != null && !name.equals(role.getName())) {
            // Check uniqueness if name changed
            if (role.getPsp() == null) {
                if (roleRepository.findByNameAndPspIsNull(name).isPresent()) {
                    throw new IllegalArgumentException("System role with this name already exists");
                }
            } else {
                if (roleRepository.findByNameAndPsp(name, role.getPsp()).isPresent()) {
                    throw new IllegalArgumentException("Role with this name already exists for this PSP");
                }
            }
            role.setName(name);
        }

        if (description != null) {
            role.setDescription(description);
        }

        return roleRepository.save(role);
    }

    public Role getRoleById(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
    }

    @Transactional
    @CacheEvict(cacheNames = "users", allEntries = true)
    public void deleteRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        // Prevent deleting system roles if needed, or check usage
        if (role.isSystemRole()) {
            // Optional: allow deleting system custom roles but not default ones?
            // For now allow, but maybe check if users are assigned?
            // Assuming DB constraint will block if users assigned.
        }

        roleRepository.delete(role);
    }

    @PostConstruct
    public void initDefaultRoles() {
        initializeSystemRole("SUPER_ADMIN",       "Super Administrator",    Set.of(Permission.values()));
        initializeSystemRole("ADMIN",             "Platform Administrator", Set.of(Permission.values()));
        initializeSystemRole("PLATFORM_ADMIN",    "Platform Administrator",
                Set.of(Permission.MANAGE_USERS, Permission.MANAGE_ROLES, Permission.MANAGE_PSP,
                        Permission.MANAGE_RULES, Permission.CONFIGURE_SYSTEM,
                        Permission.VIEW_AUDIT_LOGS, Permission.MERCHANT_VIEW,
                        Permission.MERCHANT_EDIT, Permission.REPORT_VIEW));
        // W19-5 fix: this used to grant only VIEW_CASES/VIEW_TRANSACTION_DETAILS/
        // VIEW_SCREENING_RESULTS/VIEW_SAR/MANAGE_PSP_THEME -- a PSP_ADMIN role that couldn't
        // manage its own PSP's users or rules, contradicting the role's entire purpose. V127's
        // per-PSP seed defines a much richer "full control within their PSP" set, but that
        // migration only ever ran for the handful of demo PSPs that existed at the time -- every
        // PSP registered afterward has no per-PSP PSP_ADMIN row, so createPspUser's role lookup
        // (findByNameAndPsp -> falls back to findByNameAndPspIsNull) resolves to THIS global
        // template for every real, non-demo PSP. Brought in line with V127's definition so newly
        // onboarded PSPs' admins can actually administer their PSP.
        initializeSystemRole("PSP_ADMIN",         "PSP Administrator",
                Set.of(Permission.VIEW_CASES, Permission.CREATE_CASES, Permission.ASSIGN_CASES,
                       Permission.CLOSE_CASES, Permission.ESCALATE_CASES, Permission.REOPEN_CASES,
                       Permission.ADD_CASE_NOTES, Permission.ADD_CASE_EVIDENCE,
                       Permission.VIEW_SAR, Permission.CREATE_SAR, Permission.APPROVE_SAR,
                       Permission.FILE_SAR, Permission.AMEND_SAR,
                       Permission.VIEW_PII, Permission.EXPORT_DATA,
                       Permission.VIEW_TRANSACTION_DETAILS, Permission.VIEW_SCREENING_RESULTS,
                       Permission.MANAGE_WATCHLISTS, Permission.WHITELIST_ENTITY,
                       Permission.MANAGE_RULES, Permission.MANAGE_PSP_THEME,
                       Permission.PSP_SETTINGS_VIEW, Permission.PSP_SETTINGS_EDIT, Permission.PSP_UI_EDIT,
                       Permission.MERCHANT_VIEW, Permission.MERCHANT_EDIT, Permission.REPORT_VIEW));
        initializeSystemRole("COMPLIANCE_OFFICER","Compliance Officer",
                Set.of(Permission.VIEW_CASES, Permission.VIEW_SCREENING_RESULTS,
                       Permission.ASSIGN_CASES, Permission.FILE_SAR, Permission.ADD_CASE_EVIDENCE,
                       Permission.ESCALATE_CASES, Permission.MANAGE_WATCHLISTS));
        initializeSystemRole("INVESTIGATOR",      "Investigator",
                Set.of(Permission.VIEW_CASES, Permission.VIEW_TRANSACTION_DETAILS,
                       Permission.VIEW_SCREENING_RESULTS, Permission.ADD_CASE_EVIDENCE,
                       Permission.ASSIGN_CASES));
        initializeSystemRole("ANALYST",           "Risk Analyst",
                Set.of(Permission.VIEW_CASES, Permission.VIEW_TRANSACTION_DETAILS,
                       Permission.VIEW_SCREENING_RESULTS));
        initializeSystemRole("VIEWER",            "Read Only",
                Set.of(Permission.VIEW_CASES, Permission.VIEW_SAR,
                       Permission.VIEW_TRANSACTION_DETAILS));

        // W19-2 fix: SCREENING_ANALYST and PSP_ANALYST are documented, real values in the
        // UserRole enum and are referenced across 17+ @PreAuthorize annotations and permission
        // checks throughout the codebase (KycDueDiligenceController, DocumentController,
        // MerchantController, CasePermissionService, PspIsolationService, ...), but no Role row
        // with either name was ever seeded -- every one of those branches was permanently dead,
        // since role assignment only offers roles that actually exist in the roles table.
        initializeSystemRole("SCREENING_ANALYST", "Sanctions Screening Specialist",
                Set.of(Permission.VIEW_CASES, Permission.VIEW_SAR, Permission.VIEW_SCREENING_RESULTS,
                       Permission.MANAGE_WATCHLISTS, Permission.WHITELIST_ENTITY,
                       Permission.OVERRIDE_SCREENING_MATCH, Permission.VIEW_TRANSACTION_DETAILS,
                       Permission.MERCHANT_VIEW));
        initializeSystemRole("PSP_ANALYST",       "PSP Case Analyst",
                Set.of(Permission.VIEW_CASES, Permission.ASSIGN_CASES, Permission.ADD_CASE_NOTES,
                       Permission.ADD_CASE_EVIDENCE, Permission.VIEW_SAR,
                       Permission.VIEW_TRANSACTION_DETAILS, Permission.VIEW_SCREENING_RESULTS,
                       Permission.MERCHANT_VIEW, Permission.REPORT_VIEW));

        // APP_CONTROLLER is a machine/service-account role (Grafana dashboards, reporting-config
        // service-to-service calls -- GrafanaUserContextController, PspReportingConfigService,
        // PspIsolationService all check for it by raw role-name string, not via a Permission
        // grant), so it deliberately gets no Permission set of its own -- it exists purely so a
        // service-account User can be assigned this role name at all, which was impossible before.
        initializeSystemRole("APP_CONTROLLER",    "Application Service Account", Set.of());
    }

    private void initializeSystemRole(String name, String description, Set<Permission> permissions) {
        if (roleRepository.findByNameAndPspIsNull(name).isEmpty()) {
            Role role = Role.builder()
                    .name(name)
                    .description(description)
                    .psp(null)
                    .permissions(permissions)
                    .build();
            roleRepository.save(role);
        }
    }
}
