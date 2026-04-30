package com.posgateway.aml.repository;

import com.posgateway.aml.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    // Find users by specific PSP
    java.util.List<User> findByPsp(com.posgateway.aml.entity.psp.Psp psp);

    // Find users by PSP and Role
    java.util.List<User> findByPspAndRole(com.posgateway.aml.entity.psp.Psp psp, com.posgateway.aml.entity.Role role);

    // Find system admins (psp is null)
    java.util.List<User> findByPspIsNull();

    // Find users by role name and enabled status
    java.util.List<User> findByRole_NameAndEnabled(String roleName, boolean enabled);
}
