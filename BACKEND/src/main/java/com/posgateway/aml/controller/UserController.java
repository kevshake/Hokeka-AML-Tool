package com.posgateway.aml.controller;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.PermissionService;
import com.posgateway.aml.service.UserService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/users")
public class UserController {
    private static final String USER_MGMT =
            "hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PLATFORM_ADMIN','ROLE_PSP_ADMIN')";

    private final UserService userService;
    private final PermissionService permissionService;
    private final PspRepository pspRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;

    public UserController(UserService userService, PermissionService permissionService,
            PspRepository pspRepository, UserRepository userRepository,
            PspIsolationService pspIsolationService) {
        this.userService = userService;
        this.permissionService = permissionService;
        this.pspRepository = pspRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * List users with pagination
     * GET /users
     * 
     * Security: PSP users can only see users from their PSP.
     * Super Admin can see all users or filter by specific PSP.
     * 
     * @param currentUser Authenticated user
     * @param pspId Optional PSP ID filter (Super Admin only)
     * @param page Page number (default: 0)
     * @param size Page size (default: 25, max: 100)
     * @return Paginated list of users
     */
    @GetMapping
    @PreAuthorize(USER_MGMT)
    public ResponseEntity<org.springframework.data.domain.Page<User>> listUsers(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) Long pspId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "25") int size) {
        if (currentUser == null) {
            return ResponseEntity.notFound().build();
        }
        if (!permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_USERS)) {
            throw new SecurityException("Not authorized");
        }

        int safeSize = Math.max(1, Math.min(size, 100)); // Max 100 per page
        int safePage = Math.max(0, page);

        // Build Specification for filtering
        org.springframework.data.jpa.domain.Specification<User> spec = 
            org.springframework.data.jpa.domain.Specification.where(null);

        // PSP Isolation Logic
        if (currentUser != null && pspIsolationService.isPlatformAdministrator(currentUser)) {
            if (pspId != null) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("psp").get("pspId"), pspId));
            }
        } else if (currentUser != null) {
            Long userPspId = pspIsolationService.getCurrentUserPspId();
            if (pspId != null && !pspId.equals(userPspId)) {
                throw new SecurityException("Cannot access other PSP's users");
            }
            spec = spec.and((root, query, cb) -> cb.equal(root.get("psp").get("pspId"), userPspId));
        }

        // Create Pageable with sorting by created date descending
        org.springframework.data.domain.Pageable pageable = 
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Execute query with pagination
        org.springframework.data.domain.Page<User> pageResult = 
            userRepository.findAll(spec, pageable);

        return ResponseEntity.ok(pageResult);
    }

    /**
     * Get user by ID
     * GET /users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize(USER_MGMT)
    public ResponseEntity<User> getUserById(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_USERS)) {
            throw new SecurityException("Not authorized");
        }

        User user = userService.getUserById(id);
        
        if (currentUser != null && !pspIsolationService.isPlatformAdministrator(currentUser)) {
            Long userPspId = pspIsolationService.getCurrentUserPspId();
            if (user.getPsp() == null || !user.getPsp().getPspId().equals(userPspId)) {
                throw new SecurityException("Cannot access user from another PSP");
            }
        }
        
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @PreAuthorize(USER_MGMT)
    public ResponseEntity<User> createUser(@AuthenticationPrincipal User currentUser,
            @RequestBody CreateUserRequest req) {
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_USERS)) {
            throw new SecurityException("Not authorized");
        }

        Psp targetPsp = null;

        if (currentUser != null && pspIsolationService.isPlatformAdministrator(currentUser)) {
            if (req.getPspId() != null) {
                targetPsp = pspRepository.findById(req.getPspId())
                        .orElseThrow(() -> new IllegalArgumentException("PSP not found"));
            }
        } else if (currentUser != null) {
            targetPsp = currentUser.getPsp();
            if (targetPsp == null) {
                throw new SecurityException("PSP-scoped user has no PSP assigned");
            }
            if (req.getPspId() != null && !java.util.Objects.equals(req.getPspId(), targetPsp.getPspId())) {
                throw new SecurityException("Cannot create user for another PSP");
            }
        }

        User newUser = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .passwordHash(req.getPassword()) // Service will encode
                .build();

        return ResponseEntity.ok(userService.createUser(newUser, req.getRoleId(), targetPsp));
    }

    @PutMapping("/{id}")
    @PreAuthorize(USER_MGMT)
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest req,
            @AuthenticationPrincipal User currentUser) {
        // Authorization checks...
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_USERS)) {
            throw new SecurityException("Not authorized");
        }
        // PSP scoping: PSP users can only manage users from their own PSP. Without this, a
        // PSP_ADMIN with MANAGE_USERS could edit any user platform-wide by iterating {id} — the
        // permission check above is necessary but not sufficient, since it says nothing about
        // WHICH user is being touched. Same pattern already enforced by toggleUserStatusPatch below.
        requireSamePsp(currentUser, id);

        User updates = new User();
        updates.setFirstName(req.getFirstName());
        updates.setLastName(req.getLastName());
        updates.setEmail(req.getEmail());

        return ResponseEntity.ok(userService.updateUser(id, updates, req.getRoleId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(USER_MGMT)
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_USERS)) {
            throw new SecurityException("Not authorized");
        }
        // Same cross-tenant gap as updateUser above: deleting by {id} alone let a PSP admin delete
        // another PSP's user.
        requireSamePsp(currentUser, id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Legacy alias for {@link #toggleUserStatusPatch}; not called by the current frontend (which
     * uses {@code PATCH /{id}/toggle}), but left reachable directly via the API, so it must enforce
     * the same tenant isolation rather than relying on the frontend never calling it.
     */
    @PostMapping("/{id}/{action}")
    @PreAuthorize(USER_MGMT)
    public ResponseEntity<Void> toggleUserStatus(@PathVariable Long id, @PathVariable String action,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_USERS)) {
            throw new SecurityException("Not authorized");
        }
        requireSamePsp(currentUser, id);

        boolean enable = "enable".equalsIgnoreCase(action);
        userService.toggleUserStatus(id, enable);
        return ResponseEntity.ok().build();
    }

    /**
     * Tenant isolation for write operations addressed by user id alone. A platform admin
     * ({@code currentUser.getPsp() == null}) may act on any user; a PSP-scoped admin may only act
     * on a user belonging to their own PSP. Mirrors the check already present in
     * {@link #toggleUserStatusPatch} — extracted here so update/delete/toggle-legacy enforce it
     * identically instead of three near-copies drifting apart.
     *
     * @throws SecurityException if a PSP-scoped caller targets a user outside their own PSP, or the
     *                            target user does not exist (do not leak existence via a different
     *                            error shape than "not authorized")
     */
    private void requireSamePsp(User currentUser, Long targetUserId) {
        if (currentUser == null || pspIsolationService.isPlatformAdministrator(currentUser)) {
            return;
        }
        User targetUser = userService.getUserById(targetUserId);
        if (targetUser == null || targetUser.getPsp() == null
                || !targetUser.getPsp().getPspId().equals(currentUser.getPsp().getPspId())) {
            throw new SecurityException("Cannot manage user from another PSP");
        }
    }

    /**
     * Get current user profile
     * GET /users/me
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(currentUser);
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateCurrentUser(@AuthenticationPrincipal User currentUser, @RequestBody UpdateProfileRequest req) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.updateProfile(currentUser.getId(), req.getFirstName(), req.getLastName(), req.getEmail()));
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal User currentUser, @RequestBody ChangePasswordRequest req) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        try {
            userService.changePassword(currentUser.getId(), req.getCurrentPassword(), req.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public static class UpdateProfileRequest {
        private String firstName;
        private String lastName;
        private String email;

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;

        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    public static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        private String email;
        private Long roleId;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public Long getRoleId() {
            return roleId;
        }

        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize(USER_MGMT)
    public ResponseEntity<Void> toggleUserStatusPatch(@PathVariable Long id, @RequestBody ToggleUserRequest req,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser != null && !permissionService.hasPermission(currentUser.getRole(), Permission.MANAGE_USERS)) {
            throw new SecurityException("Not authorized");
        }
        
        // Fetch target user to verify PSP isolation
        User targetUser = userService.getUserById(id);
        
        if (currentUser != null && !pspIsolationService.isPlatformAdministrator(currentUser)) {
            Long userPspId = pspIsolationService.getCurrentUserPspId();
            if (targetUser.getPsp() == null || !targetUser.getPsp().getPspId().equals(userPspId)) {
                throw new SecurityException("Cannot manage user from another PSP");
            }
        }

        userService.toggleUserStatus(id, req.isEnabled());
        return ResponseEntity.ok().build();
    }

    public static class ToggleUserRequest {
        private boolean enabled;
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class CreateUserRequest {
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String password;
        private Long roleId;
        private Long pspId;

        public CreateUserRequest() {
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Long getRoleId() {
            return roleId;
        }

        public void setRoleId(Long roleId) {
            this.roleId = roleId;
        }

        public Long getPspId() {
            return pspId;
        }

        public void setPspId(Long pspId) {
            this.pspId = pspId;
        }
    }
}
