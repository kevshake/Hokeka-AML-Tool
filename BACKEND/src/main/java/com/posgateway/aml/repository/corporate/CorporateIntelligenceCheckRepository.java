package com.posgateway.aml.repository.corporate;

import com.posgateway.aml.entity.corporate.CorporateIntelligenceCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorporateIntelligenceCheckRepository extends JpaRepository<CorporateIntelligenceCheck, Long> {
    List<CorporateIntelligenceCheck> findByMerchant_MerchantIdOrderByCheckedAtDesc(Long merchantId);
    Optional<CorporateIntelligenceCheck> findFirstByMerchant_MerchantIdOrderByCheckedAtDesc(Long merchantId);
    Optional<CorporateIntelligenceCheck> findByIdAndPspId(Long id, Long pspId);
}
