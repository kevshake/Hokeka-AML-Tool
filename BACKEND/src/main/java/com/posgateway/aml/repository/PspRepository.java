package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.Psp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PSP Repository
 */
@Repository
public interface PspRepository extends JpaRepository<Psp, Long> {

    Optional<Psp> findByPspCode(String pspCode);

    List<Psp> findByStatus(String status);

    List<Psp> findByCountry(String country);

    boolean existsByPspCode(String pspCode);

    @Query("SELECT p FROM Psp p WHERE p.status = 'ACTIVE' AND p.isTestMode = false")
    List<Psp> findActiveProductionPsps();

    @Query("SELECT COUNT(p) FROM Psp p WHERE p.status = :status")
    long countByStatus(@Param("status") String status);

}
