package com.posgateway.aml.repository.chargeback;

import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargebackDisputeRepository extends JpaRepository<ChargebackDispute, Long> {

    Optional<ChargebackDispute> findByDeduplicationId(String deduplicationId);

    List<ChargebackDispute> findByPspIdOrderByCreatedAtDesc(Long pspId);

    List<ChargebackDispute> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    List<ChargebackDispute> findAllByOrderByCreatedAtDesc();
}
