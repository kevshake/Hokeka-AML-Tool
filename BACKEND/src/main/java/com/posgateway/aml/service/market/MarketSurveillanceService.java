package com.posgateway.aml.service.market;

import com.posgateway.aml.dto.market.MarketSurveillanceDtos.*;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.market.*;
import com.posgateway.aml.entity.multiasset.FinancialCrimeSignalType;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.market.*;
import com.posgateway.aml.repository.multiasset.MultiAssetCustomerRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MarketSurveillanceService {
    private final MarketOrderRepository orderRepository;
    private final MarketExecutionRepository executionRepository;
    private final MarketSurveillanceSignalRepository signalRepository;
    private final MultiAssetCustomerRepository customerRepository;
    private final AlertRepository alertRepository;
    private final PspIsolationService pspIsolationService;
    private final BigDecimal largeOrderNotional;
    private final long rapidCancellationSeconds;
    private final BigDecimal offMarketDeviationPercent;
    private final long closeWindowMinutes;

    public MarketSurveillanceService(MarketOrderRepository orderRepository,
            MarketExecutionRepository executionRepository,
            MarketSurveillanceSignalRepository signalRepository,
            MultiAssetCustomerRepository customerRepository,
            AlertRepository alertRepository,
            PspIsolationService pspIsolationService,
            @Value("${market.surveillance.large-order-notional:50000}") BigDecimal largeOrderNotional,
            @Value("${market.surveillance.rapid-cancellation-seconds:120}") long rapidCancellationSeconds,
            @Value("${market.surveillance.off-market-deviation-percent:5}") BigDecimal offMarketDeviationPercent,
            @Value("${market.surveillance.close-window-minutes:5}") long closeWindowMinutes) {
        this.orderRepository = orderRepository;
        this.executionRepository = executionRepository;
        this.signalRepository = signalRepository;
        this.customerRepository = customerRepository;
        this.alertRepository = alertRepository;
        this.pspIsolationService = pspIsolationService;
        this.largeOrderNotional = largeOrderNotional;
        this.rapidCancellationSeconds = rapidCancellationSeconds;
        this.offMarketDeviationPercent = offMarketDeviationPercent;
        this.closeWindowMinutes = closeWindowMinutes;
    }

    @Transactional
    public SurveillanceResult<OrderResponse> placeOrder(PlaceOrderRequest request) {
        return placeOrderForPsp(requiredPspId(), request);
    }

    @Transactional
    public SurveillanceResult<OrderResponse> placeOrderForPsp(Long pspId, PlaceOrderRequest request) {
        validatePspId(pspId);
        Optional<MarketOrder> duplicate = orderRepository.findByPspIdAndExternalOrderId(
                pspId, request.externalOrderId().trim());
        if (duplicate.isPresent()) {
            return new SurveillanceResult<>(toOrder(duplicate.get()), List.of());
        }
        MultiAssetCustomer customer = customer(request.customerId(), pspId);
        MarketOrder order = new MarketOrder();
        order.setPspId(pspId);
        order.setExternalOrderId(request.externalOrderId().trim());
        order.setCustomer(customer);
        order.setAccountReference(request.accountReference().trim());
        order.setBeneficialOwnerReference(trim(request.beneficialOwnerReference()));
        order.setInstrumentId(request.instrumentId().trim());
        order.setSymbol(trim(request.symbol()));
        order.setSide(request.side());
        order.setOrderType(request.orderType().trim().toUpperCase());
        order.setQuantity(request.quantity());
        order.setLimitPrice(request.limitPrice());
        order.setCurrency(request.currency().trim().toUpperCase());
        order.setVenue(trim(request.venue()));
        order.setPlacedAt(request.placedAt() != null ? request.placedAt() : LocalDateTime.now());
        order.setMarketCloseAt(request.marketCloseAt());
        order.setReferencePrice(request.referencePrice());
        order.setDeviceFingerprint(trim(request.deviceFingerprint()));
        order.setMetadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : new LinkedHashMap<>());
        order = orderRepository.save(order);

        List<SignalResponse> signals = new ArrayList<>();
        List<MarketOrder> layered = orderRepository
                .findByPspIdAndCustomer_IdAndInstrumentIdAndSideAndStatusInAndPlacedAtAfter(
                        pspId, customer.getId(), order.getInstrumentId(), order.getSide(),
                        List.of(MarketOrderStatus.OPEN, MarketOrderStatus.PARTIALLY_FILLED),
                        order.getPlacedAt().minusMinutes(10));
        long priceLevels = layered.stream().map(MarketOrder::getLimitPrice).filter(Objects::nonNull).distinct().count();
        BigDecimal totalNotional = layered.stream().map(this::notional).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (layered.size() >= 3 && priceLevels >= 3 && totalNotional.compareTo(largeOrderNotional) >= 0) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("orderIds", layered.stream().map(MarketOrder::getExternalOrderId).toList());
            evidence.put("priceLevels", priceLevels);
            evidence.put("totalNotional", totalNotional);
            evidence.put("windowMinutes", 10);
            signals.add(createSignal(order, null, "LAYERING_ORDER_PATTERN", 55,
                    "Multiple same-side orders at distinct price levels created a layered order-book pattern.", evidence));
        }
        return new SurveillanceResult<>(toOrder(order), signals);
    }

    @Transactional
    public SurveillanceResult<OrderResponse> cancelOrder(String externalOrderId, CancelOrderRequest request) {
        return cancelOrderForPsp(requiredPspId(), externalOrderId, request);
    }

    @Transactional
    public SurveillanceResult<OrderResponse> cancelOrderForPsp(
            Long pspId, String externalOrderId, CancelOrderRequest request) {
        validatePspId(pspId);
        MarketOrder order = orderRepository.findByPspIdAndExternalOrderId(pspId, externalOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Market order not found"));
        if (order.getStatus() == MarketOrderStatus.CANCELLED) {
            return new SurveillanceResult<>(toOrder(order), List.of());
        }
        if (order.getStatus() == MarketOrderStatus.FILLED || order.getStatus() == MarketOrderStatus.REJECTED) {
            throw new IllegalStateException("Only open or partially filled orders can be cancelled");
        }
        LocalDateTime cancelledAt = request.cancelledAt() != null ? request.cancelledAt() : LocalDateTime.now();
        if (cancelledAt.isBefore(order.getPlacedAt())) {
            throw new IllegalArgumentException("Cancellation cannot precede order placement");
        }
        order.setCancelledAt(cancelledAt);
        order.setStatus(MarketOrderStatus.CANCELLED);
        order = orderRepository.save(order);

        List<SignalResponse> signals = new ArrayList<>();
        long lifetimeSeconds = Duration.between(order.getPlacedAt(), cancelledAt).getSeconds();
        BigDecimal fillRatio = order.getExecutedQuantity().divide(order.getQuantity(), 6, RoundingMode.HALF_UP);
        if (lifetimeSeconds <= rapidCancellationSeconds && fillRatio.compareTo(new BigDecimal("0.10")) <= 0
                && notional(order).compareTo(largeOrderNotional) >= 0) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("lifetimeSeconds", lifetimeSeconds);
            evidence.put("notional", notional(order));
            evidence.put("fillRatio", fillRatio);
            evidence.put("cancellationReason", trim(request.reason()));
            signals.add(createSignal(order, null, "RAPID_LARGE_ORDER_CANCELLATION", 60,
                    "A large order was cancelled rapidly with little or no execution, consistent with spoofing risk.", evidence));
        }
        return new SurveillanceResult<>(toOrder(order), signals);
    }

    @Transactional
    public SurveillanceResult<ExecutionResponse> recordExecution(RecordExecutionRequest request) {
        return recordExecutionForPsp(requiredPspId(), request);
    }

    @Transactional
    public SurveillanceResult<ExecutionResponse> recordExecutionForPsp(Long pspId, RecordExecutionRequest request) {
        validatePspId(pspId);
        Optional<MarketExecution> duplicate = executionRepository.findByPspIdAndExternalExecutionId(
                pspId, request.externalExecutionId().trim());
        if (duplicate.isPresent()) return new SurveillanceResult<>(toExecution(duplicate.get()), List.of());

        MarketOrder order = orderRepository.findByPspIdAndExternalOrderId(pspId, request.externalOrderId().trim())
                .orElseThrow(() -> new IllegalArgumentException("Market order not found"));
        if (order.getStatus() == MarketOrderStatus.CANCELLED || order.getStatus() == MarketOrderStatus.REJECTED) {
            throw new IllegalStateException("Cancelled or rejected orders cannot receive executions");
        }
        BigDecimal remaining = order.getQuantity().subtract(order.getExecutedQuantity());
        if (request.quantity().compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Execution quantity exceeds remaining order quantity");
        }
        MultiAssetCustomer counterparty = request.counterpartyCustomerId() != null
                ? customer(request.counterpartyCustomerId(), pspId) : null;
        MarketExecution execution = new MarketExecution();
        execution.setPspId(pspId);
        execution.setExternalExecutionId(request.externalExecutionId().trim());
        execution.setOrder(order);
        execution.setCustomer(order.getCustomer());
        execution.setInstrumentId(order.getInstrumentId());
        execution.setSide(order.getSide());
        execution.setQuantity(request.quantity());
        execution.setPrice(request.price());
        execution.setCurrency(order.getCurrency());
        execution.setExecutedAt(request.executedAt() != null ? request.executedAt() : LocalDateTime.now());
        execution.setCounterpartyCustomer(counterparty);
        execution.setCounterpartyReference(trim(request.counterpartyReference()));
        execution.setCounterpartyBeneficialOwnerReference(trim(request.counterpartyBeneficialOwnerReference()));
        execution.setVenue(trim(request.venue()) != null ? trim(request.venue()) : order.getVenue());
        execution.setMarketCloseAt(request.marketCloseAt() != null ? request.marketCloseAt() : order.getMarketCloseAt());
        execution.setReferencePrice(request.referencePrice() != null ? request.referencePrice() : order.getReferencePrice());
        execution.setSettlementAccountReference(trim(request.settlementAccountReference()));
        execution.setMetadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : new LinkedHashMap<>());
        execution = executionRepository.save(execution);

        order.setExecutedQuantity(order.getExecutedQuantity().add(request.quantity()));
        order.setStatus(order.getExecutedQuantity().compareTo(order.getQuantity()) == 0
                ? MarketOrderStatus.FILLED : MarketOrderStatus.PARTIALLY_FILLED);
        orderRepository.save(order);

        List<SignalResponse> signals = assessExecution(execution);
        return new SurveillanceResult<>(toExecution(execution), signals);
    }

    @Transactional
    public OrderResponse rejectOrderForPsp(Long pspId, String externalOrderId, LocalDateTime rejectedAt,
            String reason, Map<String, Object> evidence) {
        validatePspId(pspId);
        MarketOrder order = orderRepository.findByPspIdAndExternalOrderId(pspId, externalOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Market order not found"));
        if (order.getStatus() == MarketOrderStatus.REJECTED) return toOrder(order);
        if (order.getStatus() == MarketOrderStatus.FILLED || order.getStatus() == MarketOrderStatus.CANCELLED) {
            throw new IllegalStateException("Filled or cancelled orders cannot be rejected");
        }
        order.setStatus(MarketOrderStatus.REJECTED);
        Map<String, Object> metadata = new LinkedHashMap<>(
                order.getMetadata() == null ? Map.of() : order.getMetadata());
        metadata.put("rejectedAt", rejectedAt != null ? rejectedAt : LocalDateTime.now());
        metadata.put("rejectionReason", trim(reason));
        if (evidence != null && !evidence.isEmpty()) metadata.put("rejectionEvidence", evidence);
        order.setMetadata(metadata);
        return toOrder(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(int page, int size) {
        return orderRepository.findByPspIdOrderByPlacedAtDesc(requiredPspId(),
                PageRequest.of(page, Math.min(Math.max(size, 1), 100))).map(this::toOrder);
    }

    @Transactional(readOnly = true)
    public Page<SignalResponse> listSignals(int page, int size) {
        return signalRepository.findByPspIdOrderByCreatedAtDesc(requiredPspId(),
                PageRequest.of(page, Math.min(Math.max(size, 1), 100))).map(this::toSignal);
    }

    @Transactional
    public SignalResponse reviewSignal(Long id, ReviewSignalRequest request) {
        Long pspId = requiredPspId();
        MarketSurveillanceSignal signal = signalRepository.findByIdAndPspId(id, pspId)
                .orElseThrow(() -> new IllegalArgumentException("Market surveillance signal not found"));
        if (request.status() == MarketSignalStatus.OPEN) {
            throw new IllegalArgumentException("Review status must record an investigator outcome");
        }
        User reviewer = pspIsolationService.getCurrentUser();
        signal.setStatus(request.status());
        signal.setReviewedAt(LocalDateTime.now());
        signal.setReviewedBy(reviewer);
        signal.setReviewNotes(request.notes().trim());
        return toSignal(signalRepository.save(signal));
    }

    private List<SignalResponse> assessExecution(MarketExecution execution) {
        List<SignalResponse> signals = new ArrayList<>();
        MarketOrder order = execution.getOrder();
        boolean sameCustomer = execution.getCounterpartyCustomer() != null
                && execution.getCounterpartyCustomer().getId().equals(order.getCustomer().getId());
        boolean sameOwner = sameNonBlank(order.getBeneficialOwnerReference(),
                execution.getCounterpartyBeneficialOwnerReference());
        if (sameCustomer || sameOwner) {
            Map<String, Object> evidence = baseExecutionEvidence(execution);
            evidence.put("sameCustomer", sameCustomer);
            evidence.put("sameBeneficialOwner", sameOwner);
            signals.add(createSignal(order, execution, "POSSIBLE_WASH_TRADE", 80,
                    "Execution counterparties resolve to the same customer or beneficial owner.", evidence));
        }

        BigDecimal deviation = deviationPercent(execution.getPrice(), execution.getReferencePrice());
        if (deviation != null && deviation.compareTo(offMarketDeviationPercent) >= 0) {
            Map<String, Object> evidence = baseExecutionEvidence(execution);
            evidence.put("referencePrice", execution.getReferencePrice());
            evidence.put("deviationPercent", deviation);
            signals.add(createSignal(order, execution, "OFF_MARKET_EXECUTION", 50,
                    "Execution price deviates materially from the supplied market reference.", evidence));
        }

        if (execution.getMarketCloseAt() != null && !execution.getExecutedAt().isAfter(execution.getMarketCloseAt())) {
            long minutesToClose = Duration.between(execution.getExecutedAt(), execution.getMarketCloseAt()).toMinutes();
            if (minutesToClose <= closeWindowMinutes && deviation != null
                    && deviation.compareTo(offMarketDeviationPercent) >= 0) {
                Map<String, Object> evidence = baseExecutionEvidence(execution);
                evidence.put("minutesToClose", minutesToClose);
                evidence.put("deviationPercent", deviation);
                signals.add(createSignal(order, execution, "MARKING_THE_CLOSE", 65,
                        "A materially off-market execution occurred within the configured market-close window.", evidence));
            }
        }

        if (execution.getCounterpartyReference() != null) {
            List<MarketExecution> repeated = executionRepository
                    .findByPspIdAndCustomer_IdAndInstrumentIdAndCounterpartyReferenceAndExecutedAtAfter(
                            execution.getPspId(), execution.getCustomer().getId(), execution.getInstrumentId(),
                            execution.getCounterpartyReference(), execution.getExecutedAt().minusDays(30));
            if (repeated.size() >= 3) {
                Map<String, Object> evidence = baseExecutionEvidence(execution);
                evidence.put("executionCount30Days", repeated.size());
                evidence.put("executionIds", repeated.stream().map(MarketExecution::getExternalExecutionId).toList());
                signals.add(createSignal(order, execution, "REPEATED_PREARRANGED_COUNTERPARTY", 55,
                        "Repeated executions in the same instrument involve the same counterparty reference.", evidence));
            }
        }
        return signals;
    }

    private SignalResponse createSignal(MarketOrder order, MarketExecution execution, String code, int score,
            String description, Map<String, Object> evidence) {
        MarketSurveillanceSignal signal = new MarketSurveillanceSignal();
        signal.setPspId(order.getPspId());
        signal.setCustomer(order.getCustomer());
        signal.setOrder(order);
        signal.setExecution(execution);
        signal.setScenarioCode(code);
        signal.setSignalType(FinancialCrimeSignalType.MARKET_ABUSE);
        signal.setScore(score);
        signal.setSeverity(score >= 70 ? "CRITICAL" : score >= 50 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW");
        signal.setDescription(description);
        signal.setEvidence(evidence);
        signal = signalRepository.save(signal);
        createUnifiedAlert(signal);
        return toSignal(signal);
    }

    private void createUnifiedAlert(MarketSurveillanceSignal signal) {
        String sourceReference = String.valueOf(signal.getId());
        if (alertRepository.existsByPspIdAndSourceTypeAndSourceReference(
                signal.getPspId(), "MARKET_SURVEILLANCE", sourceReference)) return;
        Alert alert = new Alert();
        alert.setPspId(signal.getPspId());
        alert.setMultiAssetCustomerId(signal.getCustomer().getId());
        alert.setSourceType("MARKET_SURVEILLANCE");
        alert.setSourceReference(sourceReference);
        alert.setScore(signal.getScore() / 100.0);
        alert.setAction(signal.getScore() >= 70 ? "REVIEW" : "ALERT");
        alert.setStatus("open");
        alert.setSeverity(signal.getScore() >= 70 ? "CRITICAL" : "WARN");
        alert.setReason(signal.getScenarioCode() + ": " + signal.getDescription());
        alertRepository.save(alert);
    }

    private Map<String, Object> baseExecutionEvidence(MarketExecution execution) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("externalExecutionId", execution.getExternalExecutionId());
        evidence.put("externalOrderId", execution.getOrder().getExternalOrderId());
        evidence.put("instrumentId", execution.getInstrumentId());
        evidence.put("side", execution.getSide().name());
        evidence.put("quantity", execution.getQuantity());
        evidence.put("price", execution.getPrice());
        evidence.put("currency", execution.getCurrency());
        evidence.put("counterpartyReference", execution.getCounterpartyReference());
        return evidence;
    }

    private BigDecimal notional(MarketOrder order) {
        BigDecimal price = order.getLimitPrice() != null ? order.getLimitPrice() : order.getReferencePrice();
        return price != null ? order.getQuantity().multiply(price) : BigDecimal.ZERO;
    }

    private BigDecimal deviationPercent(BigDecimal price, BigDecimal reference) {
        if (price == null || reference == null || reference.signum() == 0) return null;
        return price.subtract(reference).abs().divide(reference, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private MultiAssetCustomer customer(Long id, Long pspId) {
        return customerRepository.findByIdAndPspId(id, pspId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found in current PSP"));
    }

    private Long requiredPspId() {
        Long pspId = pspIsolationService.getCurrentUserPspId();
        if (pspId == null) throw new SecurityException("Authenticated user has no PSP context");
        return pspId;
    }

    private void validatePspId(Long pspId) {
        if (pspId == null || pspId <= 0) throw new SecurityException("A valid PSP context is required");
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private boolean sameNonBlank(String left, String right) {
        return left != null && right != null && !left.isBlank() && left.equalsIgnoreCase(right);
    }

    private OrderResponse toOrder(MarketOrder order) {
        return new OrderResponse(order.getId(), order.getExternalOrderId(), order.getCustomer().getId(),
                order.getCustomer().getDisplayName(), order.getAccountReference(), order.getInstrumentId(),
                order.getSymbol(), order.getSide(), order.getOrderType(), order.getQuantity(),
                order.getExecutedQuantity(), order.getLimitPrice(), order.getCurrency(), order.getVenue(),
                order.getStatus(), order.getPlacedAt(), order.getCancelledAt());
    }

    private ExecutionResponse toExecution(MarketExecution execution) {
        return new ExecutionResponse(execution.getId(), execution.getExternalExecutionId(),
                execution.getOrder().getId(), execution.getOrder().getExternalOrderId(),
                execution.getCustomer().getId(), execution.getInstrumentId(), execution.getSide(),
                execution.getQuantity(), execution.getPrice(), execution.getCurrency(), execution.getExecutedAt(),
                execution.getCounterpartyReference());
    }

    private SignalResponse toSignal(MarketSurveillanceSignal signal) {
        return new SignalResponse(signal.getId(), signal.getCustomer().getId(),
                signal.getCustomer().getDisplayName(), signal.getOrder() != null ? signal.getOrder().getId() : null,
                signal.getExecution() != null ? signal.getExecution().getId() : null, signal.getScenarioCode(),
                signal.getSignalType().name(), signal.getSeverity(), signal.getScore(), signal.getDescription(),
                signal.getEvidence(), signal.getStatus(), signal.getCreatedAt(), signal.getReviewedAt(),
                signal.getReviewedBy() != null ? signal.getReviewedBy().getUsername() : null, signal.getReviewNotes());
    }
}
