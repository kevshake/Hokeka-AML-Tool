package com.posgateway.aml.service.market;

import com.posgateway.aml.dto.market.MarketSurveillanceDtos.*;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.market.*;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.market.*;
import com.posgateway.aml.repository.multiasset.MultiAssetCustomerRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketSurveillanceServiceTest {
    private final MarketOrderRepository orderRepository = mock(MarketOrderRepository.class);
    private final MarketExecutionRepository executionRepository = mock(MarketExecutionRepository.class);
    private final MarketSurveillanceSignalRepository signalRepository = mock(MarketSurveillanceSignalRepository.class);
    private final MultiAssetCustomerRepository customerRepository = mock(MultiAssetCustomerRepository.class);
    private final AlertRepository alertRepository = mock(AlertRepository.class);
    private final PspIsolationService isolationService = mock(PspIsolationService.class);
    private final AtomicLong ids = new AtomicLong(100);
    private final MultiAssetCustomer customer = new MultiAssetCustomer();
    private MarketSurveillanceService service;

    @BeforeEach
    void setUp() {
        customer.setId(5L);
        customer.setPspId(45L);
        customer.setDisplayName("Test Trader");
        when(isolationService.getCurrentUserPspId()).thenReturn(45L);
        when(customerRepository.findByIdAndPspId(5L, 45L)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any(MarketOrder.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(executionRepository.save(any(MarketExecution.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(signalRepository.save(any(MarketSurveillanceSignal.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new MarketSurveillanceService(orderRepository, executionRepository, signalRepository,
                customerRepository, alertRepository, isolationService, new BigDecimal("50000"),
                120, new BigDecimal("5"), 5);
    }

    @Test
    void rapidLargeCancellationCreatesMarketAbuseSignalAndUnifiedAlert() {
        MarketOrder order = order(LocalDateTime.now().minusSeconds(60));
        when(orderRepository.findByPspIdAndExternalOrderId(45L, "ORD-1")).thenReturn(Optional.of(order));

        SurveillanceResult<OrderResponse> result = service.cancelOrder("ORD-1",
                new CancelOrderRequest(LocalDateTime.now(), "Client cancelled"));

        assertThat(result.record().status()).isEqualTo(MarketOrderStatus.CANCELLED);
        assertThat(result.signals()).extracting(SignalResponse::scenarioCode)
                .containsExactly("RAPID_LARGE_ORDER_CANCELLATION");
        assertThat(result.signals()).extracting(SignalResponse::signalType)
                .containsOnly("MARKET_ABUSE");
        verify(alertRepository).save(argThat(alert ->
                "MARKET_SURVEILLANCE".equals(alert.getSourceType()) && alert.getMultiAssetCustomerId().equals(5L)));
    }

    @Test
    void washTradeNearCloseRetainsMarketContextAsSeparateSignals() {
        LocalDateTime executionTime = LocalDateTime.now();
        MarketOrder order = order(executionTime.minusMinutes(10));
        order.setReferencePrice(new BigDecimal("100"));
        order.setMarketCloseAt(executionTime.plusMinutes(2));
        when(orderRepository.findByPspIdAndExternalOrderId(45L, "ORD-1")).thenReturn(Optional.of(order));
        when(executionRepository.findByPspIdAndExternalExecutionId(45L, "EXE-1")).thenReturn(Optional.empty());
        when(executionRepository.findByPspIdAndCustomer_IdAndInstrumentIdAndCounterpartyReferenceAndExecutedAtAfter(
                anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(List.of());

        RecordExecutionRequest request = new RecordExecutionRequest("EXE-1", "ORD-1",
                BigDecimal.valueOf(100), new BigDecimal("110"), executionTime, 5L,
                "SELF", "OWNER-1", "XNAS", executionTime.plusMinutes(2),
                new BigDecimal("100"), "SETTLE-1", Map.of());

        SurveillanceResult<ExecutionResponse> result = service.recordExecution(request);

        assertThat(result.signals()).extracting(SignalResponse::scenarioCode)
                .containsExactly("POSSIBLE_WASH_TRADE", "OFF_MARKET_EXECUTION", "MARKING_THE_CLOSE");
        assertThat(result.signals()).allMatch(signal -> signal.evidence().containsKey("externalExecutionId"));
        verify(alertRepository, times(3)).save(any(Alert.class));
    }

    private MarketOrder order(LocalDateTime placedAt) {
        MarketOrder order = new MarketOrder();
        order.setId(10L);
        order.setPspId(45L);
        order.setExternalOrderId("ORD-1");
        order.setCustomer(customer);
        order.setAccountReference("BROKER-1");
        order.setBeneficialOwnerReference("OWNER-1");
        order.setInstrumentId("KE0000001");
        order.setSide(MarketOrderSide.BUY);
        order.setOrderType("LIMIT");
        order.setQuantity(BigDecimal.valueOf(1000));
        order.setExecutedQuantity(BigDecimal.ZERO);
        order.setLimitPrice(BigDecimal.valueOf(100));
        order.setCurrency("KES");
        order.setStatus(MarketOrderStatus.OPEN);
        order.setPlacedAt(placedAt);
        return order;
    }

    private <T> T withId(T entity) {
        Object id = ReflectionTestUtils.getField(entity, "id");
        if (id == null) ReflectionTestUtils.setField(entity, "id", ids.incrementAndGet());
        return entity;
    }
}
