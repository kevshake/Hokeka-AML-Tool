package com.posgateway.aml.repository.market;

import com.posgateway.aml.entity.market.MarketExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketExecutionRepository extends JpaRepository<MarketExecution, Long> {
    Optional<MarketExecution> findByPspIdAndExternalExecutionId(Long pspId, String externalExecutionId);
    List<MarketExecution> findByPspIdAndCustomer_IdAndInstrumentIdAndCounterpartyReferenceAndExecutedAtAfter(
            Long pspId, Long customerId, String instrumentId, String counterpartyReference, LocalDateTime after);
}
