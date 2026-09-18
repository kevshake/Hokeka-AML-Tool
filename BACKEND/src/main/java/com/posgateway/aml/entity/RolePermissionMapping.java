package com.posgateway.aml.entity;

import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.UserRole;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Role-Permission Mapping Entity
 * Maps UserRoles to specific Permissions for granular access control
 */
@Entity
@Table(name = "role_permission_mappings", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_role", "permission" })
})
public class RolePermissionMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    private UserRole userRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permission;

    @Column(name = "granted_by")
    private String grantedBy; // Username who granted this permission

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(columnDefinition = "TEXT")
    private String notes; // Reason for granting

    public RolePermissionMapping() {
    }

    public RolePermissionMapping(Long id, UserRole userRole, Permission permission, String grantedBy,
            LocalDateTime grantedAt, String notes) {
        this.id = id;
        this.userRole = userRole;
        this.permission = permission;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public void setUserRole(UserRole userRole) {
        this.userRole = userRole;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(String grantedBy) {
        this.grantedBy = grantedBy;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(LocalDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
    }

    public static RolePermissionMappingBuilder builder() {
        return new RolePermissionMappingBuilder();
    }

    public static class RolePermissionMappingBuilder {
        private Long id;
        private UserRole userRole;
        private Permission permission;
        private String grantedBy;
        private LocalDateTime grantedAt;
        private String notes;

        RolePermissionMappingBuilder() {
        }

        public RolePermissionMappingBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public RolePermissionMappingBuilder userRole(UserRole userRole) {
            this.userRole = userRole;
            return this;
        }

        public RolePermissionMappingBuilder permission(Permission permission) {
            this.permission = permission;
            return this;
        }

        public RolePermissionMappingBuilder grantedBy(String grantedBy) {
            this.grantedBy = grantedBy;
            return this;
        }

        public RolePermissionMappingBuilder grantedAt(LocalDateTime grantedAt) {
            this.grantedAt = grantedAt;
            return this;
        }

        public RolePermissionMappingBuilder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public RolePermissionMapping build() {
            return new RolePermissionMapping(id, userRole, permission, grantedBy, grantedAt, notes);
        }

        public String toString() {
            return "RolePermissionMapping.RolePermissionMappingBuilder(id=" + this.id + ", userRole=" + this.userRole
                    + ", permission=" + this.permission + ", grantedBy=" + this.grantedBy + ", grantedAt="
                    + this.grantedAt + ", notes=" + this.notes + ")";
        }
    }
}
