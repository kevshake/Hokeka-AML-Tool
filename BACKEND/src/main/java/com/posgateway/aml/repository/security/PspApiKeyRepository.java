package com.posgateway.aml.repository.security;

import com.posgateway.aml.entity.security.PspApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PspApiKeyRepository extends JpaRepository<PspApiKey, Long> {
    List<PspApiKey> findByPspPspIdOrderByCreatedAtDesc(Long pspId);

    /** Looks a presented key up by its SHA-256 hash, eagerly loading the owning PSP. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT k FROM PspApiKey k JOIN FETCH k.psp WHERE k.keyHash = :keyHash")
    java.util.Optional<PspApiKey> findByKeyHashWithPsp(
            @org.springframework.data.repository.query.Param("keyHash") String keyHash);
}
