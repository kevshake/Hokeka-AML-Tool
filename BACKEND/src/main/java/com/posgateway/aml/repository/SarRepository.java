package com.posgateway.aml.repository;

import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.SarStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SarRepository extends JpaRepository<SuspiciousActivityReport, Long> {

    /**
     * Find SARs by status
     */
    List<SuspiciousActivityReport> findByStatus(SarStatus status);
}
