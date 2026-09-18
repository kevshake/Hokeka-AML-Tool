package com.posgateway.aml.dto.multiasset;

import com.posgateway.aml.entity.multiasset.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MultiAssetDtos {
    private MultiAssetDtos() {}

    public record CreateCustomerRequest(
            @NotBlank String externalCustomerId,
            @NotBlank String customerType,
            @NotBlank String displayName,
            String countryCode,
            String riskTier,
            String kycStatus) {}

    public record CreateAssetAccountRequest(
            @NotBlank String externalAccountId,
            @NotNull AssetClass assetClass,
            @NotNull AssetAccountType accountType,
            String providerName,
            String currency,
            String countryCode,
            String publicAddress,
            Map<String, Object> metadata,
            ProductDomain productDomain) {}

    public record CreateCustomerRelationshipRequest(
            @NotNull Long relatedCustomerId,
            @NotNull CustomerRelationshipType relationshipType,
            BigDecimal ownershipPercentage,
            Map<String, Object> metadata) {}

    public record IngestTransactionRequest(
            @NotBlank String externalTransactionId,
            @NotNull Long customerId,
            Long sourceAccountId,
            Long destinationAccountId,
            @NotNull AssetClass assetClass,
            @NotNull MultiAssetTransactionType transactionType,
            @NotNull @Positive BigDecimal amount,
            BigDecimal fiatEquivalentUsd,
            @NotBlank String currency,
            String assetSymbol,
            BigDecimal quantity,
            BigDecimal unitPrice,
            LocalDateTime executedAt,
            String countryCode,
            String deviceFingerprint,
            String ipAddress,
            String counterpartyReference,
            String fundingPartyCustomerId,
            String sourceNetwork,
            String destinationNetwork,
            String originatorName,
            String originatorAccount,
            String beneficiaryName,
            String beneficiaryAccount,
            String merchantCategory,
            Map<String, Object> metadata,
            ProductDomain productDomain) {}

    public record CustomerSummary(
            Long id,
            String externalCustomerId,
            String customerType,
            String displayName,
            String countryCode,
            String riskTier,
            String kycStatus,
            LocalDateTime lastReviewedAt,
            LocalDateTime createdAt) {}

    public record AccountSummary(
            Long id,
            String externalAccountId,
            AssetClass assetClass,
            ProductDomain productDomain,
            AssetAccountType accountType,
            String providerName,
            String currency,
            String countryCode,
            String publicAddress,
            String status,
            Map<String, Object> metadata) {}

    public record CustomerRelationshipResponse(
            Long id,
            Long relatedCustomerId,
            String relatedCustomerExternalId,
            String relatedCustomerName,
            CustomerRelationshipType relationshipType,
            BigDecimal ownershipPercentage,
            Map<String, Object> metadata,
            LocalDateTime createdAt) {}

    public record RiskSignalResponse(
            Long id,
            Long transactionId,
            Long customerId,
            String signalCode,
            FinancialCrimeSignalType signalType,
            ProductDomain productDomain,
            String severity,
            int scoreImpact,
            String description,
            Map<String, Object> evidence,
            LocalDateTime createdAt) {}

    public record TransactionResponse(
            Long id,
            String externalTransactionId,
            Long customerId,
            AssetClass assetClass,
            ProductDomain productDomain,
            ProductDomain sourceProductDomain,
            ProductDomain destinationProductDomain,
            MultiAssetTransactionType transactionType,
            BigDecimal amount,
            BigDecimal fiatEquivalentUsd,
            String currency,
            String assetSymbol,
            LocalDateTime executedAt,
            String counterpartyReference,
            String sourceNetwork,
            String destinationNetwork,
            boolean travelRuleRequired,
            TravelRuleStatus travelRuleStatus,
            int riskScore,
            RiskDecision decision,
            List<RiskSignalResponse> signals) {}

    public record Customer360Response(
            CustomerSummary customer,
            List<CustomerRelationshipResponse> relationships,
            List<AccountSummary> accounts,
            List<TransactionResponse> recentTransactions,
            List<RiskSignalResponse> recentSignals,
            Map<AssetClass, BigDecimal> volumeByAssetClass,
            Map<AssetClass, Long> transactionCountByAssetClass,
            Map<ProductDomain, BigDecimal> volumeByProductDomain,
            Map<ProductDomain, Long> transactionCountByProductDomain,
            List<Map<String, Object>> topCounterparties,
            int compositeRiskScore) {}
}
