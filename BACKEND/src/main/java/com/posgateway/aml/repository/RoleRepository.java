package com.posgateway.aml.repository;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.psp.Psp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // Find system roles (where PSP is null)
    List<Role> findByPspIsNull();

    // Find roles for a specific PSP
    List<Role> findByPsp(Psp psp);

    // Find system roles OR roles for a specific PSP
    List<Role> findByPspIsNullOrPsp(Psp psp);

    Optional<Role> findByNameAndPsp(String name, Psp psp);

    Optional<Role> findByNameAndPspIsNull(String name);
}
