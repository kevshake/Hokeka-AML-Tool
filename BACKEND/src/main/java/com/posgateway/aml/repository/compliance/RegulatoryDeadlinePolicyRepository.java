package com.posgateway.aml.repository.compliance;

import com.posgateway.aml.entity.compliance.RegulatoryDeadlinePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegulatoryDeadlinePolicyRepository extends JpaRepository<RegulatoryDeadlinePolicy, Long> {
    List<RegulatoryDeadlinePolicy> findByReportTypeAndActiveTrue(String reportType);
}
