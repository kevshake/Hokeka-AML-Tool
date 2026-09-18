package com.posgateway.aml.dto.crypto;

import com.posgateway.aml.entity.crypto.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class VirtualAssetDtos {
    private VirtualAssetDtos() {}

    public record SaveVaspRequest(
            @NotBlank String legalName, String tradingName, @NotBlank String jurisdiction,
            String regulatorName, String licenceNumber, @NotNull VaspLicenseStatus licenceStatus,
            LocalDate licenceValidUntil, String registrationNumber, String website,
            List<String> travelRuleProtocols, String sanctionsStatus,
            List<Map<String, Object>> beneficialOwnership, List<Map<String, Object>> walletClusters,
            @Min(0) @Max(100) int riskScore, @NotNull VaspTransferDecision transferDecision,
            String reviewNotes, LocalDateTime nextReviewAt) {}

    public record VaspResponse(
            Long id, String legalName, String tradingName, String jurisdiction, String regulatorName,
            String licenceNumber, VaspLicenseStatus licenceStatus, LocalDate licenceValidUntil,
            String registrationNumber, String website, List<String> travelRuleProtocols,
            String sanctionsStatus, LocalDateTime sanctionsScreenedAt,
            String sanctionsProvider, int sanctionsMatchCount, LocalDateTime sanctionsNextScreeningAt,
            List<Map<String, Object>> beneficialOwnership, List<Map<String, Object>> walletClusters,
            int riskScore, String riskLevel, VaspTransferDecision transferDecision,
            String reviewNotes, LocalDateTime lastReviewedAt, LocalDateTime nextReviewAt) {}

    public record RegisterWalletRequest(
            @NotNull Long assetAccountId, Long vaspId, @NotBlank String network,
            String addressLabel, String ownershipType, @Min(1) @Max(8760) Integer screeningIntervalHours) {}

    public record WalletProfileResponse(
            Long id, Long assetAccountId, Long customerId, String customerName, Long vaspId, String vaspName,
            String walletAddress, String network, String addressLabel, String ownershipType, String status,
            int screeningIntervalHours, LocalDateTime lastScreenedAt, LocalDateTime nextScreeningAt,
            Integer latestRiskScore, List<String> latestCategories, String latestProvider,
            String latestProviderReference, boolean screeningAvailable, String screeningStatus) {}

    public record WalletScreeningResponse(
            Long id, Long walletProfileId, Long customerId, String customerName, Long transactionId,
            String screenedAddress, String network, WalletScreeningTrigger triggerType,
            String provider, String providerReference, boolean available, Integer riskScore,
            List<String> categories, BigDecimal directExposurePercent, BigDecimal indirectExposurePercent,
            Integer maximumExposureDepth, List<Map<String, Object>> attributions,
            Map<String, Object> evidence, String unavailableReason, LocalDateTime screenedAt, LocalDate retainUntil) {}

    public record VaspScreeningResponse(
            Long id, Long vaspId, String vaspName, String subjectName, String subjectType,
            String provider, boolean available, String status, int matchCount,
            List<Map<String, Object>> matches, Map<String, Object> evidence,
            LocalDateTime screenedAt, LocalDate retainUntil) {}

    public record CryptoTransactionSummary(
            Long id, String externalTransactionId, Long customerId, String customerName,
            String transactionType, BigDecimal amount, BigDecimal fiatEquivalentUsd,
            String currency, String assetSymbol, String counterpartyReference,
            String sourceNetwork, String destinationNetwork, int riskScore,
            String decision, String travelRuleStatus, LocalDateTime executedAt) {}

    public record SaveTravelRulePolicyRequest(
            @NotBlank String jurisdiction, @NotBlank String policyCode, @NotNull @PositiveOrZero BigDecimal thresholdUsd,
            boolean appliesToAllTransfers, @NotEmpty List<String> requiredFields,
            boolean verifyOriginator, boolean verifyBeneficiary, List<String> acceptedProtocols,
            @Min(7) int retentionYears, @NotNull LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil, boolean enabled, String legalReference) {}

    public record TravelRulePolicyResponse(
            Long id, String jurisdiction, String policyCode, BigDecimal thresholdUsd,
            boolean appliesToAllTransfers, List<String> requiredFields, boolean verifyOriginator,
            boolean verifyBeneficiary, List<String> acceptedProtocols, int retentionYears,
            LocalDateTime effectiveFrom, LocalDateTime effectiveUntil, boolean enabled, String legalReference) {}

    public record PrepareTravelRuleRequest(
            @NotNull Long transactionId, @NotBlank String jurisdiction,
            Long originatorVaspId, Long beneficiaryVaspId, String protocol,
            @NotNull Map<String, Object> payload) {}

    public record VerifyIdentityRequest(@NotBlank String party, boolean verified, String evidenceReference) {}

    public record TravelRuleTransferResponse(
            Long id, Long transactionId, String externalTransactionId, Long policyId, String policyCode,
            Long originatorVaspId, String originatorVaspName, Long beneficiaryVaspId, String beneficiaryVaspName,
            String jurisdiction, TravelRuleTransferStatus status,
            IdentityVerificationStatus originatorVerification, IdentityVerificationStatus beneficiaryVerification,
            String originatorVerificationReference, String originatorVerifiedBy, LocalDateTime originatorVerifiedAt,
            String beneficiaryVerificationReference, String beneficiaryVerifiedBy, LocalDateTime beneficiaryVerifiedAt,
            String protocol, String payloadHash, String providerMessageId, int transmissionAttempts,
            LocalDateTime transmittedAt, LocalDateTime acknowledgedAt, String failureReason,
            LocalDate retainUntil, LocalDateTime createdAt) {}

    public record CreateRegulatorGrantRequest(
            @NotBlank String regulatorName, @NotBlank String jurisdiction,
            @NotEmpty List<String> scopes, List<String> allowedIpAddresses,
            @NotNull @Future LocalDateTime expiresAt) {}

    public record RegulatorGrantResponse(
            Long id, String regulatorName, String jurisdiction, List<String> scopes,
            List<String> allowedIpAddresses, LocalDateTime expiresAt, LocalDateTime revokedAt,
            LocalDateTime lastAccessedAt, String createdBy, LocalDateTime createdAt,
            String accessKey) {}
}
