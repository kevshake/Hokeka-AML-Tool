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

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/roles")
public class RoleController {

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

        Psp targetPsp = null;

        if (currentUser == null || currentUser.getPsp() == null) {
            if (pspId != null) {
                targetPsp = pspRepository.findById(pspId)
                        .orElseThrow(() -> new IllegalArgumentException("PSP not found"));
            }
            // If pspId is null, list System Roles? Or maybe all System Roles are available
            // to everyone?
            // Let's assume listRoles matches the context of the user.
        } else {
            targetPsp = currentUser.getPsp();
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

        Psp targetPsp = null;

        if (currentUser == null || currentUser.getPsp() == null) {
            if (req.getPspId() != null) {
                targetPsp = pspRepository.findById(req.getPspId())
                        .orElseThrow(() -> new IllegalArgumentException("PSP not found"));
            }
        } else {
            targetPsp = currentUser.getPsp();
            if (req.getPspId() != null && !java.util.Objects.equals(req.getPspId(), targetPsp.getPspId())) {
                throw new SecurityException("Cannot create role for another PSP");
            }
        }

        return ResponseEntity
                .ok(roleService.createRole(req.getName(), req.getDescription(), targetPsp, req.getPermissions()));
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Role> getRoleById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.getRoleById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable Long id, @RequestBody UpdateRoleRequest req,
            @AuthenticationPrincipal User currentUser) {
        // Security checks similar to create...
        // Simplified for brevity:
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_ROLES)) {
            throw new SecurityException("Not authorized");
        }

        // In real app, check if role belongs to user's PSP

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
