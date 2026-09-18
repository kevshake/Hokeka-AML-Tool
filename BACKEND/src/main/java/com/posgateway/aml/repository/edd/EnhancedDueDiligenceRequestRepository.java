package com.posgateway.aml.repository.edd;

import com.posgateway.aml.entity.edd.EnhancedDueDiligenceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnhancedDueDiligenceRequestRepository
        extends JpaRepository<EnhancedDueDiligenceRequest, Long> {

    Optional<EnhancedDueDiligenceRequest> findByMerchantId(Long merchantId);

    List<EnhancedDueDiligenceRequest> findByStatus(String status);
}
