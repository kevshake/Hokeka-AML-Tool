package com.posgateway.aml.repository.mobilemoney;

import com.posgateway.aml.entity.mobilemoney.MobileMoneyTransactionContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MobileMoneyTransactionContextRepository extends JpaRepository<MobileMoneyTransactionContext, Long> {
    Optional<MobileMoneyTransactionContext> findByPspIdAndTransactionId(Long pspId, Long transactionId);
    Page<MobileMoneyTransactionContext> findByPspIdOrderByExecutedAtDesc(Long pspId, Pageable pageable);
    List<MobileMoneyTransactionContext> findTop500ByPspIdAndExecutedAtAfterOrderByExecutedAtDesc(
            Long pspId, LocalDateTime executedAfter);
}
