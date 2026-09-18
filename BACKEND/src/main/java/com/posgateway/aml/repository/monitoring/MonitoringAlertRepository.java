package com.posgateway.aml.repository.monitoring;

import com.posgateway.aml.entity.monitoring.MonitoringAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MonitoringAlertRepository extends JpaRepository<MonitoringAlert, Long> {

    Page<MonitoringAlert> findByMerchant_Psp_PspIdOrderByCreatedAtDesc(Long pspId, Pageable pageable);

    Page<MonitoringAlert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<MonitoringAlert> findByMerchant_MerchantIdOrderByCreatedAtDesc(Long merchantId);

    @Query("""
            SELECT COUNT(a) FROM MonitoringAlert a
            WHERE a.acknowledged = false
            AND (:pspId IS NULL OR a.merchant.psp.pspId = :pspId)
            """)
    long countUnacknowledged(@Param("pspId") Long pspId);
}
