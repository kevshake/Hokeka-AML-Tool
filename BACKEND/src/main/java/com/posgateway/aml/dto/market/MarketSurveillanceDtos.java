package com.posgateway.aml.dto.market;

import com.posgateway.aml.entity.market.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public final class MarketSurveillanceDtos {
    private MarketSurveillanceDtos() {}

    public record PlaceOrderRequest(
            @NotBlank String externalOrderId,
            @NotNull Long customerId,
            @NotBlank String accountReference,
            String beneficialOwnerReference,
            @NotBlank String instrumentId,
            String symbol,
            @NotNull MarketOrderSide side,
            @NotBlank String orderType,
            @NotNull @Positive BigDecimal quantity,
            @Positive BigDecimal limitPrice,
            @NotBlank @Size(min = 3, max = 3) String currency,
            String venue,
            LocalDateTime placedAt,
            LocalDateTime marketCloseAt,
            @Positive BigDecimal referencePrice,
            String deviceFingerprint,
            Map<String, Object> metadata) {}

    public record RecordExecutionRequest(
            @NotBlank String externalExecutionId,
            @NotBlank String externalOrderId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @Positive BigDecimal price,
            LocalDateTime executedAt,
            Long counterpartyCustomerId,
            String counterpartyReference,
            String counterpartyBeneficialOwnerReference,
            String venue,
            LocalDateTime marketCloseAt,
            @Positive BigDecimal referencePrice,
            String settlementAccountReference,
            Map<String, Object> metadata) {}

    public record CancelOrderRequest(LocalDateTime cancelledAt, String reason) {}

    public record ReviewSignalRequest(@NotNull MarketSignalStatus status, @NotBlank String notes) {}

    public record OrderResponse(Long id, String externalOrderId, Long customerId, String customerName,
            String accountReference, String instrumentId, String symbol, MarketOrderSide side,
            String orderType, BigDecimal quantity, BigDecimal executedQuantity, BigDecimal limitPrice,
            String currency, String venue, MarketOrderStatus status, LocalDateTime placedAt,
            LocalDateTime cancelledAt) {}

    public record ExecutionResponse(Long id, String externalExecutionId, Long orderId,
            String externalOrderId, Long customerId, String instrumentId, MarketOrderSide side,
            BigDecimal quantity, BigDecimal price, String currency, LocalDateTime executedAt,
            String counterpartyReference) {}

    public record SignalResponse(Long id, Long customerId, String customerName, Long orderId,
            Long executionId, String scenarioCode, String signalType, String severity, int score,
            String description, Map<String, Object> evidence, MarketSignalStatus status,
            LocalDateTime createdAt, LocalDateTime reviewedAt, String reviewedBy, String reviewNotes) {}

    public record SurveillanceResult<T>(T record, java.util.List<SignalResponse> signals) {}
}
