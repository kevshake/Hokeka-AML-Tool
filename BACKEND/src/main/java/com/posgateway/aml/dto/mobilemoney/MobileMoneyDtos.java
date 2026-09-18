package com.posgateway.aml.dto.mobilemoney;

import com.posgateway.aml.dto.multiasset.MultiAssetDtos.RiskSignalResponse;
import com.posgateway.aml.dto.multiasset.MultiAssetDtos.TransactionResponse;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyEntityType;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyEventType;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyRelationshipType;
import com.posgateway.aml.entity.multiasset.MultiAssetTransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MobileMoneyDtos {
    private MobileMoneyDtos() {}

    public record IngestMobileMoneyRequest(
            @NotBlank String externalTransactionId,
            @NotNull Long customerId,
            Long sourceAccountId,
            Long destinationAccountId,
            @NotNull MultiAssetTransactionType transactionType,
            @NotNull MobileMoneyEventType eventType,
            @NotNull @Positive BigDecimal amount,
            BigDecimal fiatEquivalentUsd,
            @NotBlank String currency,
            LocalDateTime executedAt,
            String countryCode,
            @NotBlank String walletReference,
            String counterpartyWalletReference,
            String counterpartyReference,
            String fundingPartyCustomerId,
            String deviceFingerprint,
            String agentDeviceFingerprint,
            String simIdentifierHash,
            String agentReference,
            String merchantReference,
            String merchantCategory,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal agentLatitude,
            BigDecimal agentLongitude,
            String cellId,
            LocalDateTime simChangedAt,
            LocalDateTime deviceRegisteredAt,
            LocalDateTime walletPreviousActivityAt,
            BigDecimal agentFloatBefore,
            BigDecimal agentFloatAfter,
            boolean crossBorder,
            Map<String, Object> metadata) {}

    public record MobileMoneyContextResponse(
            Long id,
            Long transactionId,
            String externalTransactionId,
            Long customerId,
            String customerName,
            String walletReference,
            String counterpartyWalletReference,
            MobileMoneyEventType eventType,
            BigDecimal amount,
            String currency,
            LocalDateTime executedAt,
            String deviceFingerprint,
            String agentReference,
            String merchantReference,
            String cellId,
            boolean crossBorder,
            int riskScore,
            String decision,
            Map<String, Object> metadata) {}

    public record MobileMoneyRiskProfileResponse(
            Long id,
            MobileMoneyEntityType entityType,
            String entityReference,
            int riskScore,
            String riskLevel,
            long signalCount,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            LocalDateTime lastAssessedAt,
            Long latestTransactionId,
            Map<String, Object> attributes) {}

    public record MobileMoneyNetworkEdgeResponse(
            Long id,
            MobileMoneyEntityType fromEntityType,
            String fromEntityReference,
            MobileMoneyEntityType toEntityType,
            String toEntityReference,
            MobileMoneyRelationshipType relationshipType,
            long occurrenceCount,
            BigDecimal cumulativeAmount,
            String currency,
            LocalDateTime firstSeenAt,
            LocalDateTime lastSeenAt,
            Long latestTransactionId) {}

    public record MobileMoneyAssessmentResponse(
            TransactionResponse transaction,
            MobileMoneyContextResponse context,
            List<RiskSignalResponse> signals,
            List<MobileMoneyRiskProfileResponse> riskProfiles,
            List<MobileMoneyNetworkEdgeResponse> networkEdges) {}

    public record MobileMoneyNetworkResponse(
            MobileMoneyEntityType entityType,
            String entityReference,
            MobileMoneyRiskProfileResponse riskProfile,
            List<MobileMoneyNetworkEdgeResponse> edges) {}
}
