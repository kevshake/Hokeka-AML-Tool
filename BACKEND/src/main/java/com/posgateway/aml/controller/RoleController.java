package com.posgateway.aml.controller;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.service.PermissionService;
import com.posgateway.aml.service.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;

// @RequiredArgsConstructor removed
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_SUPER_ADMIN','ROLE_PLATFORM_ADMIN','ROLE_PSP_ADMIN','MANAGE_USERS','MANAGE_ROLES')")
@RestController
@RequestMapping("/roles")
public class RoleController {

    private static final java.util.Set<String> PLATFORM_ADMIN_ROLES =
            java.util.Set.of("ADMIN", "SUPER_ADMIN", "PLATFORM_ADMIN");

    private static boolean isPlatformAdmin(User user) {
        return user != null && user.getRole() != null && user.getRole().getName() != null
                && PLATFORM_ADMIN_ROLES.contains(user.getRole().getName().toUpperCase());
    }

    private final RoleService roleService;
    private final PermissionService permissionService;
    private final PspRepository pspRepository;
    private final com.posgateway.aml.service.UserService userService;

    public RoleController(RoleService roleService, PermissionService permissionService, PspRepository pspRepository,
            com.posgateway.aml.service.UserService userService) {
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.pspRepository = pspRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<Role>> listRoles(@AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) Long pspId) {
        if (currentUser == null) {
            currentUser = userService.getSuperAdmin().orElse(null);
        }

        // Platform admins can list any PSP's roles (or all system roles when pspId is null).
        // Tenant users are constrained to their own PSP regardless of the query param.
        Psp targetPsp;
        if (isPlatformAdmin(currentUser)) {
            targetPsp = pspId != null
                    ? pspRepository.findById(pspId)
                            .orElseThrow(() -> new IllegalArgumentException("PSP not found"))
                    : null;
        } else if (currentUser != null && currentUser.getPsp() != null) {
            targetPsp = currentUser.getPsp();
        } else {
            targetPsp = null;
        }

        return ResponseEntity.ok(roleService.getRolesForPsp(targetPsp));
    }

    @PostMapping
    public ResponseEntity<Role> createRole(@AuthenticationPrincipal User currentUser,
            @RequestBody CreateRoleRequest req) {
        if (currentUser == null) {
            currentUser = userService.getSuperAdmin().orElse(null);
        }

        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_ROLES)) {
            throw new SecurityException("Not authorized");
        }

        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("Role name is required");
        }

        Psp targetPsp = null;
        boolean platformAdmin = isPlatformAdmin(currentUser);

        if (req.getPspId() != null) {
            targetPsp = pspRepository.findById(req.getPspId())
                    .orElseThrow(() -> new IllegalArgumentException("PSP not found"));
            if (!platformAdmin) {
                if (currentUser == null || currentUser.getPsp() == null
                        || !java.util.Objects.equals(req.getPspId(), currentUser.getPsp().getPspId())) {
                    throw new SecurityException("Cannot create role for another PSP");
                }
            }
        } else if (!platformAdmin) {
            // Only platform admins may create cross-tenant (system) roles
            throw new SecurityException("Only platform administrators can create system-wide roles");
        }

        Set<Permission> perms = req.getPermissions() != null ? req.getPermissions() : java.util.Collections.emptySet();
        return ResponseEntity
                .ok(roleService.createRole(req.getName(), req.getDescription(), targetPsp, perms));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Role> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable Long id, @RequestBody UpdateRoleRequest req,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_ROLES)) {
            throw new SecurityException("Not authorized");
        }

        Role existing = roleService.getRoleById(id);
        if (currentUser != null && currentUser.getPsp() != null) {
            if (existing.getPsp() == null
                    || !existing.getPsp().getPspId().equals(currentUser.getPsp().getPspId())) {
                throw new SecurityException("Cannot update role outside your PSP");
            }
        }

        Role updated = roleService.updateRole(id, req.getName(), req.getDescription());
        if (req.getPermissions() != null) {
            updated = roleService.updatePermissions(id, req.getPermissions());
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_ROLES)) {
            throw new SecurityException("Not authorized");
        }
        Role existing = roleService.getRoleById(id);
        if (currentUser != null && currentUser.getPsp() != null) {
            if (existing.getPsp() == null
                    || !existing.getPsp().getPspId().equals(currentUser.getPsp().getPspId())) {
                throw new SecurityException("Cannot delete role outside your PSP");
            }
        }
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }

    public static class UpdateRoleRequest {
        private String name;
        private String description;
        private Set<Permission> permissions;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Set<Permission> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<Permission> permissions) {
            this.permissions = permissions;
        }
    }

    public static class CreateRoleRequest {
        private String name;
        private String description;
        private Long pspId;
        private Set<Permission> permissions;

        public CreateRoleRequest() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getPspId() {
            return pspId;
        }

        public void setPspId(Long pspId) {
            this.pspId = pspId;
        }

        public Set<Permission> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<Permission> permissions) {
            this.permissions = permissions;
        }
    }
}
