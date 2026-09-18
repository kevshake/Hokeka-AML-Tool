package com.posgateway.aml.repository.market;

import com.posgateway.aml.entity.market.MarketSurveillanceSignal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketSurveillanceSignalRepository extends JpaRepository<MarketSurveillanceSignal, Long> {
    Page<MarketSurveillanceSignal> findByPspIdOrderByCreatedAtDesc(Long pspId, Pageable pageable);
    Optional<MarketSurveillanceSignal> findByIdAndPspId(Long id, Long pspId);
}
