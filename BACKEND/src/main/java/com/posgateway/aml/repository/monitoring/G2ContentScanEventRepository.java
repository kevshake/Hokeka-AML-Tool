package com.posgateway.aml.repository.monitoring;

import com.posgateway.aml.entity.monitoring.G2ContentScanEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface G2ContentScanEventRepository extends JpaRepository<G2ContentScanEvent, Long> {

    List<G2ContentScanEvent> findByMerchantIdOrderByScannedAtDesc(Long merchantId, Pageable pageable);

    Page<G2ContentScanEvent> findByPspIdOrderByScannedAtDesc(Long pspId, Pageable pageable);

    Page<G2ContentScanEvent> findAllByOrderByScannedAtDesc(Pageable pageable);
}
