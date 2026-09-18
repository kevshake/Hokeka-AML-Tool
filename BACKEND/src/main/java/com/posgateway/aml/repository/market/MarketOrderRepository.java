package com.posgateway.aml.repository.market;

import com.posgateway.aml.entity.market.MarketOrder;
import com.posgateway.aml.entity.market.MarketOrderSide;
import com.posgateway.aml.entity.market.MarketOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, Long> {
    Optional<MarketOrder> findByPspIdAndExternalOrderId(Long pspId, String externalOrderId);
    Page<MarketOrder> findByPspIdOrderByPlacedAtDesc(Long pspId, Pageable pageable);
    List<MarketOrder> findByPspIdAndCustomer_IdAndInstrumentIdAndSideAndStatusInAndPlacedAtAfter(
            Long pspId, Long customerId, String instrumentId, MarketOrderSide side,
            List<MarketOrderStatus> statuses, LocalDateTime placedAfter);
}
