package com.posgateway.aml.repository;

import com.posgateway.aml.entity.RolePermissionMapping;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Repository for Role-Permission Mappings
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermissionMapping, Long> {

    /**
     * Find all permissions for a given role
     */
    List<RolePermissionMapping> findByUserRole(UserRole userRole);

    /**
     * Get all permissions for a specific role as a Set
     */
    @Query("SELECT rpm.permission FROM RolePermissionMapping rpm WHERE rpm.userRole = :role")
    Set<Permission> findPermissionsByUserRole(@Param("role") UserRole role);

    /**
     * Check if a role has a specific permission
     */
    boolean existsByUserRoleAndPermission(UserRole userRole, Permission permission);

    /**
     * Find all roles that have a specific permission
     */
    @Query("SELECT rpm.userRole FROM RolePermissionMapping rpm WHERE rpm.permission = :permission")
    Set<UserRole> findRolesWithPermission(@Param("permission") Permission permission);

    /**
     * Delete a specific role-permission mapping
     */
    void deleteByUserRoleAndPermission(UserRole userRole, Permission permission);
}
