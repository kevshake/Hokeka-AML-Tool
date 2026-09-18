package com.posgateway.aml.repository.security;

import com.posgateway.aml.entity.security.PspApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PspApiKeyRepository extends JpaRepository<PspApiKey, Long> {
    List<PspApiKey> findByPspPspIdOrderByCreatedAtDesc(Long pspId);
}
