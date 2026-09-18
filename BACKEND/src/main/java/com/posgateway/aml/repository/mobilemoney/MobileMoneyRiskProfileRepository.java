package com.posgateway.aml.repository.mobilemoney;

import com.posgateway.aml.entity.mobilemoney.MobileMoneyEntityType;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyRiskProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MobileMoneyRiskProfileRepository extends JpaRepository<MobileMoneyRiskProfile, Long> {
    Optional<MobileMoneyRiskProfile> findByPspIdAndEntityTypeAndEntityReference(
            Long pspId, MobileMoneyEntityType entityType, String entityReference);
    Page<MobileMoneyRiskProfile> findByPspIdOrderByRiskScoreDescLastAssessedAtDesc(Long pspId, Pageable pageable);
}
