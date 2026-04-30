package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
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

    @Transactional
    public Role updatePermissions(Long roleId, Set<Permission> permissions) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    @Transactional
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
        // Initialize basic system roles if they don't exist
        initializeSystemRole("ADMIN", "System Administrator", Set.of(Permission.values()));
        initializeSystemRole("VIEWER", "Read Only User",
                Set.of(Permission.VIEW_CASES, Permission.VIEW_SAR, Permission.VIEW_TRANSACTION_DETAILS));
        initializeSystemRole("COMPLIANCE_OFFICER", "Compliance Officer",
                Set.of(Permission.VIEW_CASES, Permission.VIEW_SCREENING_RESULTS, Permission.ASSIGN_CASES,
                        Permission.FILE_SAR));
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
