package com.posgateway.aml.controller.auth;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.RoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/auth")
public class RolePermissionController {

    private final RoleService roleService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public RolePermissionController(RoleService roleService, RoleRepository roleRepository,
                                    UserRepository userRepository) {
        this.roleService = roleService;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    /**
     * Roles for a PSP. Caller may only request roles for their OWN PSP unless they
     * are a platform admin (psp == null). Prevents cross-tenant role enumeration.
     */
    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getRoles(@RequestParam(required = false) Long pspId) {
        User caller = currentUser();
        Long callerPspId = caller != null && caller.getPsp() != null ? caller.getPsp().getPspId() : null;

        if (pspId != null) {
            if (callerPspId != null && !callerPspId.equals(pspId)) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
            }
            com.posgateway.aml.entity.psp.Psp psp = com.posgateway.aml.entity.psp.Psp.builder().pspId(pspId).build();
            return ResponseEntity.ok(roleService.getRolesForPsp(psp));
        }
        // No pspId requested: PSP users get their own PSP's roles, platform admins get system roles.
        if (callerPspId != null) {
            com.posgateway.aml.entity.psp.Psp psp = com.posgateway.aml.entity.psp.Psp.builder().pspId(callerPspId).build();
            return ResponseEntity.ok(roleService.getRolesForPsp(psp));
        }
        return ResponseEntity.ok(roleService.getSystemRoles());
    }

    private User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> getPermissions() {
        return ResponseEntity.ok(Arrays.asList(Permission.values()));
    }

    @GetMapping("/role-permissions")
    public ResponseEntity<Set<Permission>> getRolePermissions(@RequestParam("roleId") Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        return ResponseEntity.ok(role.getPermissions());
    }
}
