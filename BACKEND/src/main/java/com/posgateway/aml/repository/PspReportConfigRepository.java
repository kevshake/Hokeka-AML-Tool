package com.posgateway.aml.repository;

import com.posgateway.aml.entity.psp.PspReportConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PspReportConfigRepository extends JpaRepository<PspReportConfig, Long> {
    Optional<PspReportConfig> findByPsp_PspId(Long pspId);
}
