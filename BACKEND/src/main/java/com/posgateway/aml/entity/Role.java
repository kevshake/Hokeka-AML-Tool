package com.posgateway.aml.entity;

import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.model.Permission;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Dynamic Role Entity
 * Supports custom roles for both System (null PSP) and specific PSPs.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psp_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Psp psp; // NULL = System global role, NOT NULL = PSP specific role

    @ElementCollection(targetClass = Permission.class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "role_permissions_dynamic", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission")
    private Set<Permission> permissions = new HashSet<>();

    public Role() {
    }

    public Role(Long id, String name, String description, Psp psp, Set<Permission> permissions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.psp = psp;
        this.permissions = permissions != null ? permissions : new HashSet<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Psp getPsp() {
        return psp;
    }

    public void setPsp(Psp psp) {
        this.psp = psp;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    // Helper to check for system role
    public boolean isSystemRole() {
        return psp == null;
    }

    public static RoleBuilder builder() {
        return new RoleBuilder();
    }

    public static class RoleBuilder {
        private Long id;
        private String name;
        private String description;
        private Psp psp;
        private Set<Permission> permissions = new HashSet<>();

        RoleBuilder() {
        }

        public RoleBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public RoleBuilder name(String name) {
            this.name = name;
            return this;
        }

        public RoleBuilder description(String description) {
            this.description = description;
            return this;
        }

        public RoleBuilder psp(Psp psp) {
            this.psp = psp;
            return this;
        }

        public RoleBuilder permissions(Set<Permission> permissions) {
            this.permissions = permissions;
            return this;
        }

        public Role build() {
            return new Role(id, name, description, psp, permissions);
        }

        public String toString() {
            return "Role.RoleBuilder(id=" + this.id + ", name=" + this.name + ", description=" + this.description
                    + ", psp=" + this.psp + ", permissions=" + this.permissions + ")";
        }
    }
}
