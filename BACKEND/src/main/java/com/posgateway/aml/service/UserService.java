package com.posgateway.aml.service;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// @RequiredArgsConstructor removed
@Service
@SuppressWarnings("null") // Repository methods return Optional, saved entities are non-null
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getUsersByPsp(Psp psp) {
        if (psp == null) {
            // For System Admins, maybe show all? Or just system users?
            // For now, let's assume NULL psp means getting System Users.
            // If we want ALL users across all PSPs, we'd need a different method or flag.
            return userRepository.findByPspIsNull();
        }
        return userRepository.findByPsp(psp);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public User createUser(User user, Long roleId, Psp psp) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        // Validate Role belongs to PSP
        if (role.getPsp() != null && !role.getPsp().equals(psp)) {
            // Check if it's not a system role (system roles have null psp and can be
            // assigned to anyone?)
            // Usually System Roles are for System Users, but maybe we allow "View Only"
            // system role to be assigned to PSP users?
            // For strict isolation: PSP Users must have PSP Roles OR specific Global Roles
            // if allowed.
            // Simplified: PSP Users must have Role.psp == user.psp OR Role.psp == null
            // (Global)
            if (psp != null && !role.isSystemRole()) { // If user is PSP user, but role is another PSP's role
                throw new IllegalArgumentException("Cannot assign a role from a different PSP");
            }
        }

        // If user is System (psp=null), Role must be System (psp=null)
        if (psp == null && !role.isSystemRole()) {
            throw new IllegalArgumentException("System users cannot have PSP-specific roles");
        }

        user.setRole(role);
        user.setPsp(psp);
        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));

        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long userId, User updates, Long roleId) {
        User user = getUserById(userId);

        if (updates.getFirstName() != null)
            user.setFirstName(updates.getFirstName());
        if (updates.getLastName() != null)
            user.setLastName(updates.getLastName());
        if (updates.getEmail() != null)
            user.setEmail(updates.getEmail()); // Should check uniqueness if changed

        if (roleId != null) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
            // Validate role scoping matches user's PSP
            if (user.getPsp() != null && role.getPsp() != null && !role.getPsp().equals(user.getPsp())) {
                throw new IllegalArgumentException("Role does not belong to user's PSP");
            }
            user.setRole(role);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    @Transactional
    public void toggleUserStatus(Long userId, boolean enable) {
        User user = getUserById(userId);
        user.setEnabled(enable);
        userRepository.save(user);
    }

    @Transactional
    public User updateProfile(Long userId, String firstName, String lastName, String email) {
        User user = getUserById(userId);

        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(email);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);

        // In a real app, we should verify current password here.
        // However, we rely on the controller or authentication provider for now 
        // OR we can check it here if we inject AuthenticationManager or check hash manually.
        // For security, checking matches(current, hash) is best.
        
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
             throw new IllegalArgumentException("Incorrect current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public java.util.Optional<User> getSuperAdmin() {
        return userRepository.findByUsername("super.admin@aml.com"); // Matches dummy data
    }
}
