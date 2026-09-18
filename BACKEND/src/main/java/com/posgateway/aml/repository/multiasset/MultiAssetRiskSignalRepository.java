package com.posgateway.aml.repository.multiasset;

import com.posgateway.aml.entity.multiasset.MultiAssetRiskSignal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MultiAssetRiskSignalRepository extends JpaRepository<MultiAssetRiskSignal, Long> {
    List<MultiAssetRiskSignal> findTop100ByCustomerIdAndPspIdOrderByCreatedAtDesc(Long customerId, Long pspId);
    List<MultiAssetRiskSignal> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);
    Page<MultiAssetRiskSignal> findByPspIdOrderByCreatedAtDesc(Long pspId, Pageable pageable);
}
