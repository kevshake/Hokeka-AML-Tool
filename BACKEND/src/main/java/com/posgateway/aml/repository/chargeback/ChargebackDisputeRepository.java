package com.posgateway.aml.repository.chargeback;

import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargebackDisputeRepository extends JpaRepository<ChargebackDispute, Long> {

    Optional<ChargebackDispute> findByDeduplicationId(String deduplicationId);

    Optional<ChargebackDispute> findTopByCaseIdOrderByCreatedAtDesc(String caseId);

    List<ChargebackDispute> findByPspIdOrderByCreatedAtDesc(Long pspId);

    List<ChargebackDispute> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);

    List<ChargebackDispute> findAllByOrderByCreatedAtDesc();

    /** Paginated PSP-scoped dispute list for Console tables and exports. */
    Page<ChargebackDispute> findByPspId(Long pspId, Pageable pageable);

    /** Paginated merchant-scoped dispute list for Console tables and exports. */
    Page<ChargebackDispute> findByMerchantId(Long merchantId, Pageable pageable);
}
