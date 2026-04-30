package com.posgateway.aml.controller.auth;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.service.RoleService;
import org.springframework.http.ResponseEntity;
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

    public RolePermissionController(RoleService roleService, RoleRepository roleRepository) {
        this.roleService = roleService;
        this.roleRepository = roleRepository;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getRoles(@RequestParam(required = false) Long pspId) {
        if (pspId != null) {
            com.posgateway.aml.entity.psp.Psp psp = com.posgateway.aml.entity.psp.Psp.builder().pspId(pspId).build();
            return ResponseEntity.ok(roleService.getRolesForPsp(psp));
        }
        return ResponseEntity.ok(roleService.getSystemRoles());
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
