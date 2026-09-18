package com.posgateway.aml.repository.crypto;

import com.posgateway.aml.entity.crypto.TravelRuleTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.LocalDateTime;

public interface TravelRuleTransferRepository extends JpaRepository<TravelRuleTransfer, Long> {
    Optional<TravelRuleTransfer> findByIdAndPspId(Long id, Long pspId);
    Optional<TravelRuleTransfer> findByPspIdAndTransactionId(Long pspId, Long transactionId);
    Page<TravelRuleTransfer> findByPspIdOrderByCreatedAtDesc(Long pspId, Pageable pageable);
    Page<TravelRuleTransfer> findByPspIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long pspId, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
