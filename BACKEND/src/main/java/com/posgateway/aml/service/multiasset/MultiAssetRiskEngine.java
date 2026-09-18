package com.posgateway.aml.service.multiasset;

import com.posgateway.aml.client.blockchain.BlockchainAnalyticsClient;
import com.posgateway.aml.dto.multiasset.MultiAssetDtos.IngestTransactionRequest;
import com.posgateway.aml.entity.crypto.TravelRuleJurisdictionPolicy;
import com.posgateway.aml.entity.multiasset.*;
import com.posgateway.aml.model.ScreeningResult;
import com.posgateway.aml.model.ScreeningResult.EntityType;
import com.posgateway.aml.model.ScreeningResult.ScreeningStatus;
import com.posgateway.aml.repository.crypto.TravelRulePolicyRepository;
import com.posgateway.aml.service.aml.AerospikeSanctionsScreeningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Deterministic, explainable scenarios across securities, e-money, and crypto. */
@Service
public class MultiAssetRiskEngine {

    private final BlockchainAnalyticsClient blockchainAnalyticsClient;
    private final BigDecimal eMoneyDailyThreshold;
    private final BigDecimal travelRuleThresholdUsd;
    private final BigDecimal pennyStockPriceThreshold;
    private final Set<String> highRiskMerchantCategories;
    private final long prepaidRapidRedemptionHours;
    private final long prepaidRefundCountThreshold;
    private final BigDecimal tokenizedFiatDailyThresholdUsd;

    public MultiAssetRiskEngine(
            BlockchainAnalyticsClient blockchainAnalyticsClient,
            @Value("${multiasset.emoney.daily-threshold:2000}") BigDecimal eMoneyDailyThreshold,
            @Value("${multiasset.crypto.travel-rule-threshold-usd:1000}") BigDecimal travelRuleThresholdUsd,
            @Value("${multiasset.securities.penny-stock-price-threshold:5}") BigDecimal pennyStockPriceThreshold,
            @Value("${multiasset.emoney.high-risk-merchant-categories:GAMBLING,CRYPTO_OTC,ADULT}") String categories,
            @Value("${multiasset.prepaid.rapid-redemption-hours:2}") long prepaidRapidRedemptionHours,
            @Value("${multiasset.prepaid.refund-count-threshold:3}") long prepaidRefundCountThreshold,
            @Value("${multiasset.tokenized-fiat.daily-threshold-usd:10000}") BigDecimal tokenizedFiatDailyThresholdUsd) {
        this.blockchainAnalyticsClient = blockchainAnalyticsClient;
        this.eMoneyDailyThreshold = eMoneyDailyThreshold;
        this.travelRuleThresholdUsd = travelRuleThresholdUsd;
        this.pennyStockPriceThreshold = pennyStockPriceThreshold;
        this.highRiskMerchantCategories = parseCategories(categories);
        this.prepaidRapidRedemptionHours = prepaidRapidRedemptionHours;
        this.prepaidRefundCountThreshold = prepaidRefundCountThreshold;
        this.tokenizedFiatDailyThresholdUsd = tokenizedFiatDailyThresholdUsd;
    }

    /**
     * W21-7 fix: the travel-rule threshold used to be a single global @Value constant, gating
     * every jurisdiction identically -- unlike VirtualAssetComplianceService, which already looks
     * up a per-jurisdiction TravelRuleJurisdictionPolicy. Field-injected (optional) rather than a
     * constructor param so the existing MultiAssetRiskEngineTest, which constructs this class
     * directly with the original 8-arg constructor, keeps compiling unchanged.
     */
    @Autowired(required = false)
    private TravelRulePolicyRepository travelRulePolicyRepository;

    /** Optional — when absent (e.g. direct unit construction) sanctions checks are skipped. */
    @Autowired(required = false)
    private AerospikeSanctionsScreeningService sanctionsScreeningService;

    /**
     * Resolves the USD travel-rule threshold for this PSP+jurisdiction, falling back to the
     * global default when no repository is wired (e.g. this unit-constructed test instance), no
     * pspId/countryCode is available, or no active per-jurisdiction policy exists for that pair.
     */
    private BigDecimal resolveTravelRuleThresholdUsd(Long pspId, String countryCode) {
        if (travelRulePolicyRepository == null || pspId == null || countryCode == null || countryCode.isBlank()) {
            return travelRuleThresholdUsd;
        }
        String jurisdiction = countryCode.trim().toUpperCase(Locale.ROOT);
        List<TravelRuleJurisdictionPolicy> active =
                travelRulePolicyRepository.findActive(pspId, jurisdiction, LocalDateTime.now());
        return active.isEmpty() ? travelRuleThresholdUsd : active.get(0).getThresholdUsd();
    }

    public Assessment assess(MultiAssetCustomer customer,
            AssetAccount sourceAccount,
            AssetAccount destinationAccount,
            IngestTransactionRequest request,
            List<MultiAssetTransaction> recentTransactions) {
        List<SignalDraft> signals = new ArrayList<>();
        CryptoScreeningAssessment cryptoScreening = null;

        checkThirdPartyFunding(customer, request, signals);
        checkCrossAssetMovement(request, recentTransactions, signals);
        ProductDomain productDomain = productDomain(sourceAccount, destinationAccount, request);

        switch (request.assetClass()) {
            case SECURITIES -> assessSecurities(request, recentTransactions, signals);
            case E_MONEY -> {
                assessEMoney(customer, request, recentTransactions, signals);
                if (productDomain == ProductDomain.PREPAID_CLOSED_LOOP) {
                    assessPrepaid(sourceAccount, destinationAccount, request, recentTransactions, signals);
                }
            }
            case TOKENIZED_FIAT -> assessTokenizedFiat(
                    customer, sourceAccount, destinationAccount, request, recentTransactions, signals);
            case CRYPTO -> cryptoScreening =
                    assessCrypto(sourceAccount, destinationAccount, request, signals, customer.getPspId());
            case BANKING -> { }
        }

        checkSanctions(customer, request, signals);
        checkCyberIncidentContext(request, signals);

        int score = Math.min(100, signals.stream().mapToInt(SignalDraft::scoreImpact).sum());
        RiskDecision decision = score >= 70 ? RiskDecision.BLOCK
                : score >= 40 ? RiskDecision.REVIEW
                : score >= 20 ? RiskDecision.ALERT
                : RiskDecision.ALLOW;
        TravelRuleStatus travelRuleStatus = resolveTravelRuleStatus(request, customer.getPspId());
        return new Assessment(score, decision, travelRuleStatus, List.copyOf(signals), cryptoScreening);
    }

    private void assessSecurities(IngestTransactionRequest request,
            List<MultiAssetTransaction> recent, List<SignalDraft> signals) {
        LocalDateTime executedAt = timestamp(request);
        if (request.transactionType() == MultiAssetTransactionType.SELL && request.assetSymbol() != null) {
            boolean rapidLiquidation = recent.stream().anyMatch(tx ->
                    tx.getTransactionType() == MultiAssetTransactionType.BUY
                    && sameText(tx.getAssetSymbol(), request.assetSymbol())
                    && !tx.getExecutedAt().isBefore(executedAt.minusDays(7)));
            if (rapidLiquidation) {
                add(signals, "SECURITIES_RAPID_LIQUIDATION", 25,
                        "Position sold within seven days of purchase.",
                        Map.of("assetSymbol", request.assetSymbol(), "lookbackDays", 7));
            }
        }

        if ((request.transactionType() == MultiAssetTransactionType.BUY
                || request.transactionType() == MultiAssetTransactionType.SELL)
                && request.assetSymbol() != null && request.counterpartyReference() != null) {
            MultiAssetTransactionType opposite = request.transactionType() == MultiAssetTransactionType.BUY
                    ? MultiAssetTransactionType.SELL : MultiAssetTransactionType.BUY;
            boolean matchedOrder = recent.stream().anyMatch(tx ->
                    tx.getTransactionType() == opposite
                    && sameText(tx.getAssetSymbol(), request.assetSymbol())
                    && sameText(tx.getCounterpartyReference(), request.counterpartyReference())
                    && !tx.getExecutedAt().isBefore(executedAt.minusHours(1)));
            if (matchedOrder) {
                add(signals, "SECURITIES_MATCHED_ORDER", 45,
                        "Opposite-side trade with the same counterparty detected within one hour.",
                        Map.of("assetSymbol", request.assetSymbol(), "counterparty", request.counterpartyReference()));
            }
        }

        if (request.unitPrice() != null
                && request.unitPrice().compareTo(pennyStockPriceThreshold) < 0
                && request.amount().compareTo(BigDecimal.valueOf(10_000)) >= 0) {
            add(signals, "SECURITIES_LOW_PRICE_HIGH_VALUE", 20,
                    "High-value activity in a low-priced security requires enhanced review.",
                    Map.of("unitPrice", request.unitPrice(), "amount", request.amount()));
        }

        BigDecimal tradeUsd = usdValue(request);
        if (tradeUsd != null && request.transactionType() == MultiAssetTransactionType.BUY) {
            boolean fundedThenTraded = recent.stream().anyMatch(tx ->
                    tx.getTransactionType() == MultiAssetTransactionType.DEPOSIT
                    && !tx.getExecutedAt().isBefore(executedAt.minusHours(24))
                    && usdValue(tx) != null
                    && usdValue(tx).compareTo(tradeUsd.multiply(BigDecimal.valueOf(0.8))) >= 0);
            if (fundedThenTraded) {
                add(signals, "SECURITIES_RAPID_POST_FUNDING_TRADE", 25,
                        "A large deposit was followed by a securities purchase within 24 hours.",
                        Map.of("tradeValueUsd", tradeUsd));
            }
        }
    }

    private void assessEMoney(MultiAssetCustomer customer, IngestTransactionRequest request,
            List<MultiAssetTransaction> recent, List<SignalDraft> signals) {
        LocalDateTime cutoff = timestamp(request).minusHours(24);
        BigDecimal volume = recent.stream()
                .filter(tx -> tx.getAssetClass() == AssetClass.E_MONEY)
                .filter(tx -> !tx.getExecutedAt().isBefore(cutoff))
                .filter(tx -> sameText(tx.getCurrency(), request.currency()))
                .map(MultiAssetTransaction::getAmount)
                .reduce(request.amount(), BigDecimal::add);
        if (volume.compareTo(eMoneyDailyThreshold) > 0) {
            add(signals, "EMONEY_DAILY_VELOCITY", 30,
                    "E-money volume exceeded the configured 24-hour threshold.",
                    Map.of("volume", volume, "threshold", eMoneyDailyThreshold, "currency", request.currency()));
        }

        if (request.transactionType() == MultiAssetTransactionType.TOP_UP
                && request.amount().compareTo(eMoneyDailyThreshold) < 0) {
            long topUps = 1 + recent.stream()
                    .filter(tx -> tx.getAssetClass() == AssetClass.E_MONEY)
                    .filter(tx -> tx.getTransactionType() == MultiAssetTransactionType.TOP_UP)
                    .filter(tx -> !tx.getExecutedAt().isBefore(cutoff))
                    .filter(tx -> tx.getAmount().compareTo(eMoneyDailyThreshold) < 0)
                    .count();
            if (topUps >= 5) {
                add(signals, "EMONEY_STRUCTURED_TOPUPS", 35,
                        "Five or more sub-threshold wallet top-ups occurred within 24 hours.",
                        Map.of("topUpCount", topUps, "threshold", eMoneyDailyThreshold));
            }
        }

        if (customer.getCountryCode() != null && request.countryCode() != null
                && !customer.getCountryCode().equalsIgnoreCase(request.countryCode())) {
            add(signals, "EMONEY_GEOLOCATION_MISMATCH", 15,
                    "Transaction location differs from the customer's recorded country.",
                    Map.of("customerCountry", customer.getCountryCode(), "transactionCountry", request.countryCode()));
        }

        if (request.deviceFingerprint() != null) {
            boolean newDevice = recent.stream()
                    .filter(tx -> tx.getAssetClass() == AssetClass.E_MONEY)
                    .map(MultiAssetTransaction::getDeviceFingerprint)
                    .filter(Objects::nonNull)
                    .noneMatch(device -> device.equals(request.deviceFingerprint()));
            if (newDevice && !recent.isEmpty()) {
                add(signals, "EMONEY_NEW_DEVICE", 10,
                        "E-money activity originated from a device not seen in recent history.", Map.of());
            }
        }

        if (request.merchantCategory() != null
                && highRiskMerchantCategories.contains(request.merchantCategory().trim().toUpperCase())) {
            add(signals, "EMONEY_HIGH_RISK_MERCHANT", 25,
                    "E-money payment involves a configured high-risk merchant category.",
                    Map.of("merchantCategory", request.merchantCategory()));
        }
    }

    private void assessPrepaid(AssetAccount sourceAccount, AssetAccount destinationAccount,
            IngestTransactionRequest request, List<MultiAssetTransaction> recent, List<SignalDraft> signals) {
        LocalDateTime executedAt = timestamp(request);
        if (request.transactionType() == MultiAssetTransactionType.WITHDRAWAL
                || request.transactionType() == MultiAssetTransactionType.TRANSFER) {
            Optional<MultiAssetTransaction> recentLoad = recent.stream()
                    .filter(tx -> tx.getProductDomain() == ProductDomain.PREPAID_CLOSED_LOOP)
                    .filter(tx -> tx.getTransactionType() == MultiAssetTransactionType.TOP_UP
                            || tx.getTransactionType() == MultiAssetTransactionType.DEPOSIT)
                    .filter(tx -> !tx.getExecutedAt().isBefore(executedAt.minusHours(prepaidRapidRedemptionHours)))
                    .filter(tx -> sameCurrency(tx.getCurrency(), request.currency()))
                    .filter(tx -> sameRequestedAccount(tx, request))
                    .filter(tx -> tx.getAmount().compareTo(request.amount().multiply(BigDecimal.valueOf(0.8))) >= 0)
                    .findFirst();
            if (recentLoad.isPresent()) {
                Map<String, Object> evidence = new LinkedHashMap<>();
                if (recentLoad.get().getId() != null) evidence.put("loadRecordId", recentLoad.get().getId());
                if (recentLoad.get().getExternalTransactionId() != null) {
                    evidence.put("loadTransactionId", recentLoad.get().getExternalTransactionId());
                }
                evidence.put("lookbackHours", prepaidRapidRedemptionHours);
                evidence.put("loadAmount", recentLoad.get().getAmount());
                evidence.put("redemptionAmount", request.amount());
                add(signals, "PREPAID_RAPID_LOAD_REDEMPTION", 40,
                        "Stored value was redeemed or transferred shortly after loading.",
                        evidence);
            }
        }

        if (request.transactionType() == MultiAssetTransactionType.REFUND) {
            long refunds = 1 + recent.stream()
                    .filter(tx -> tx.getProductDomain() == ProductDomain.PREPAID_CLOSED_LOOP)
                    .filter(tx -> tx.getTransactionType() == MultiAssetTransactionType.REFUND)
                    .filter(tx -> !tx.getExecutedAt().isBefore(executedAt.minusHours(24)))
                    .filter(tx -> sameRequestedAccount(tx, request))
                    .count();
            if (refunds >= prepaidRefundCountThreshold) {
                add(signals, "PREPAID_REFUND_VELOCITY", 35,
                        "Repeated stored-value refunds exceeded the configured 24-hour count.",
                        Map.of("refundCount", refunds, "threshold", prepaidRefundCountThreshold));
            }
        }

        if (firstMetadataValue(request.metadata(), sourceAccount, destinationAccount,
                "programReference", "closedLoopProgramId", "issuerProgramId") == null) {
            add(signals, "PREPAID_PROGRAM_UNIDENTIFIED", 20,
                    "The closed-loop or prepaid programme reference is missing.", Map.of());
        }
    }

    private void assessTokenizedFiat(MultiAssetCustomer customer, AssetAccount sourceAccount,
            AssetAccount destinationAccount, IngestTransactionRequest request,
            List<MultiAssetTransaction> recent, List<SignalDraft> signals) {
        Object issuerVerified = firstMetadataValue(request.metadata(), sourceAccount, destinationAccount,
                "issuerVerified", "centralBankVerified");
        if (!Boolean.TRUE.equals(asBoolean(issuerVerified))) {
            add(signals, "TOKENIZED_FIAT_ISSUER_UNVERIFIED", 40,
                    "Tokenized-fiat issuer or central-bank provenance is not verified.",
                    issuerVerified == null ? Map.of("verification", "MISSING")
                            : Map.of("verification", String.valueOf(issuerVerified)));
        }

        Object ledgerReference = firstMetadataValue(request.metadata(), sourceAccount, destinationAccount,
                "ledgerReference", "settlementReference", "tokenRegistryReference");
        if (ledgerReference == null || ledgerReference.toString().isBlank()) {
            add(signals, "TOKENIZED_FIAT_LEDGER_REFERENCE_MISSING", 25,
                    "Authoritative ledger or settlement provenance is missing.", Map.of());
        }

        LocalDateTime cutoff = timestamp(request).minusHours(24);
        BigDecimal currentUsd = usdValue(request);
        if (currentUsd == null) {
            add(signals, "TOKENIZED_FIAT_FIAT_VALUE_MISSING", 20,
                    "A fiat-equivalent value is required for tokenized-fiat controls.", Map.of());
        } else {
            BigDecimal volume = recent.stream()
                    .filter(tx -> tx.getAssetClass() == AssetClass.TOKENIZED_FIAT)
                    .filter(tx -> !tx.getExecutedAt().isBefore(cutoff))
                    .map(this::usdValue)
                    .filter(Objects::nonNull)
                    .reduce(currentUsd, BigDecimal::add);
            if (volume.compareTo(tokenizedFiatDailyThresholdUsd) > 0) {
                add(signals, "TOKENIZED_FIAT_DAILY_VELOCITY", 30,
                        "Tokenized-fiat volume exceeded the configured 24-hour threshold.",
                        Map.of("volumeUsd", volume, "thresholdUsd", tokenizedFiatDailyThresholdUsd));
            }
        }

        if (customer.getCountryCode() != null && request.countryCode() != null
                && !customer.getCountryCode().equalsIgnoreCase(request.countryCode())) {
            add(signals, "TOKENIZED_FIAT_CROSS_BORDER", 20,
                    "Tokenized-fiat activity occurred outside the customer's recorded jurisdiction.",
                    Map.of("customerCountry", customer.getCountryCode(),
                            "transactionCountry", request.countryCode()));
        }
    }

    private CryptoScreeningAssessment assessCrypto(AssetAccount sourceAccount, AssetAccount destinationAccount,
            IngestTransactionRequest request, List<SignalDraft> signals, Long pspId) {
        String address = request.counterpartyReference();
        if (address == null || address.isBlank()) {
            if (destinationAccount != null && destinationAccount.getPublicAddress() != null) {
                address = destinationAccount.getPublicAddress();
            } else if (sourceAccount != null) {
                address = sourceAccount.getPublicAddress();
            }
        }
        String network = firstNonBlank(request.destinationNetwork(), request.sourceNetwork());
        BlockchainAnalyticsClient.WalletRiskResult walletRisk = blockchainAnalyticsClient.screenWallet(address, network);
        if (!walletRisk.available()) {
            add(signals, "CRYPTO_SCREENING_UNAVAILABLE", 40,
                    "Blockchain wallet intelligence was unavailable; no clean decision was made.",
                    Map.of("provider", walletRisk.provider(), "reason", walletRisk.unavailableReason()));
        } else if (walletRisk.riskScore() >= 80) {
            add(signals, "CRYPTO_HIGH_RISK_WALLET", 65,
                    "Blockchain intelligence identified severe wallet exposure.",
                    walletEvidence(walletRisk));
        } else if (walletRisk.riskScore() >= 50) {
            add(signals, "CRYPTO_ELEVATED_WALLET_RISK", 35,
                    "Blockchain intelligence identified elevated wallet exposure.",
                    walletEvidence(walletRisk));
        }

        if (request.sourceNetwork() != null && request.destinationNetwork() != null
                && !request.sourceNetwork().equalsIgnoreCase(request.destinationNetwork())) {
            add(signals, "CRYPTO_CROSS_CHAIN_TRANSFER", 20,
                    "Funds moved across blockchain networks and require cross-chain review.",
                    Map.of("sourceNetwork", request.sourceNetwork(), "destinationNetwork", request.destinationNetwork()));
        }

        BigDecimal usd = usdValue(request);
        if (usd == null) {
            add(signals, "CRYPTO_FIAT_VALUE_MISSING", 10,
                    "Fiat-equivalent value is required to evaluate jurisdictional thresholds.", Map.of());
        } else {
            BigDecimal threshold = resolveTravelRuleThresholdUsd(pspId, request.countryCode());
            if (usd.compareTo(threshold) >= 0 && !hasTravelRuleData(request)) {
                add(signals, "CRYPTO_TRAVEL_RULE_INCOMPLETE", 40,
                        "Required originator or beneficiary data is incomplete for this transfer.",
                        Map.of("fiatEquivalentUsd", usd, "configuredThresholdUsd", threshold));
            }
        }
        return new CryptoScreeningAssessment(address, network, walletRisk);
    }

    private void checkThirdPartyFunding(MultiAssetCustomer customer, IngestTransactionRequest request,
            List<SignalDraft> signals) {
        if (request.fundingPartyCustomerId() != null
                && !request.fundingPartyCustomerId().equals(customer.getExternalCustomerId())) {
            add(signals, "CROSS_ASSET_THIRD_PARTY_FUNDING", 30,
                    "Funding party does not match the owner of the monitored customer profile.",
                    Map.of("fundingPartyCustomerId", request.fundingPartyCustomerId()));
        }
    }

    private void checkCrossAssetMovement(IngestTransactionRequest request,
            List<MultiAssetTransaction> recent, List<SignalDraft> signals) {
        LocalDateTime cutoff = timestamp(request).minusHours(24);
        Optional<MultiAssetTransaction> priorOtherAsset = recent.stream()
                .filter(tx -> tx.getAssetClass() != request.assetClass())
                .filter(tx -> !tx.getExecutedAt().isBefore(cutoff))
                .findFirst();
        if (priorOtherAsset.isPresent()) {
            add(signals, "CROSS_ASSET_RAPID_MOVEMENT", 20,
                    "Customer activity moved between asset classes within 24 hours.",
                    Map.of("previousAssetClass", priorOtherAsset.get().getAssetClass().name(),
                            "currentAssetClass", request.assetClass().name()));
        }
    }

    private TravelRuleStatus resolveTravelRuleStatus(IngestTransactionRequest request, Long pspId) {
        if (request.assetClass() != AssetClass.CRYPTO) return TravelRuleStatus.NOT_REQUIRED;
        BigDecimal usd = usdValue(request);
        if (usd == null) return TravelRuleStatus.PENDING_VERIFICATION;
        BigDecimal threshold = resolveTravelRuleThresholdUsd(pspId, request.countryCode());
        if (usd.compareTo(threshold) < 0) return TravelRuleStatus.NOT_REQUIRED;
        return hasTravelRuleData(request) ? TravelRuleStatus.PENDING_VERIFICATION : TravelRuleStatus.INCOMPLETE;
    }

    private boolean hasTravelRuleData(IngestTransactionRequest request) {
        return allPresent(request.originatorName(), request.originatorAccount(),
                request.beneficiaryName(), request.beneficiaryAccount());
    }

    private boolean allPresent(String... values) {
        return Arrays.stream(values).allMatch(value -> value != null && !value.isBlank());
    }

    private Map<String, Object> walletEvidence(BlockchainAnalyticsClient.WalletRiskResult result) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("provider", result.provider());
        evidence.put("riskScore", result.riskScore());
        evidence.put("categories", result.categories());
        if (result.directExposurePercent() != null) evidence.put("directExposurePercent", result.directExposurePercent());
        if (result.indirectExposurePercent() != null) evidence.put("indirectExposurePercent", result.indirectExposurePercent());
        if (result.maximumExposureDepth() != null) evidence.put("maximumExposureDepth", result.maximumExposureDepth());
        if (result.attributions() != null && !result.attributions().isEmpty()) evidence.put("attributions", result.attributions());
        if (result.providerReference() != null) evidence.put("providerReference", result.providerReference());
        return evidence;
    }

    private BigDecimal usdValue(IngestTransactionRequest request) {
        if (request.fiatEquivalentUsd() != null) return request.fiatEquivalentUsd();
        return isUsdEquivalent(request.currency()) ? request.amount() : null;
    }

    private BigDecimal usdValue(MultiAssetTransaction transaction) {
        if (transaction.getFiatEquivalentUsd() != null) return transaction.getFiatEquivalentUsd();
        return isUsdEquivalent(transaction.getCurrency()) ? transaction.getAmount() : null;
    }

    private boolean isUsdEquivalent(String currency) {
        return currency != null && Set.of("USD", "USDT", "USDC").contains(currency.toUpperCase());
    }

    private ProductDomain productDomain(AssetAccount sourceAccount, AssetAccount destinationAccount,
            IngestTransactionRequest request) {
        if (request.productDomain() != null) return request.productDomain();
        if (sourceAccount != null) return sourceAccount.getProductDomain();
        if (destinationAccount != null) return destinationAccount.getProductDomain();
        return ProductDomain.defaultFor(request.assetClass());
    }

    private boolean sameRequestedAccount(MultiAssetTransaction transaction, IngestTransactionRequest request) {
        Set<Long> requested = new HashSet<>();
        if (request.sourceAccountId() != null) requested.add(request.sourceAccountId());
        if (request.destinationAccountId() != null) requested.add(request.destinationAccountId());
        if (requested.isEmpty()) return true;
        return transaction.getSourceAccount() != null && requested.contains(transaction.getSourceAccount().getId())
                || transaction.getDestinationAccount() != null
                && requested.contains(transaction.getDestinationAccount().getId());
    }

    private boolean sameCurrency(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private Object firstMetadataValue(Map<String, Object> requestMetadata, AssetAccount sourceAccount,
            AssetAccount destinationAccount, String... keys) {
        for (String key : keys) {
            if (requestMetadata != null && requestMetadata.get(key) != null) return requestMetadata.get(key);
            if (sourceAccount != null && sourceAccount.getMetadata().get(key) != null) {
                return sourceAccount.getMetadata().get(key);
            }
            if (destinationAccount != null && destinationAccount.getMetadata().get(key) != null) {
                return destinationAccount.getMetadata().get(key);
            }
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof String text) return Boolean.valueOf(text.trim());
        return null;
    }

    private LocalDateTime timestamp(IngestTransactionRequest request) {
        return request.executedAt() != null ? request.executedAt() : LocalDateTime.now();
    }

    private static Set<String> parseCategories(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> values = new HashSet<>();
        Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isBlank())
                .map(String::toUpperCase).forEach(values::add);
        return Set.copyOf(values);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static void add(List<SignalDraft> signals, String code, int impact,
            String description, Map<String, Object> evidence) {
        String severity = impact >= 50 ? "CRITICAL" : impact >= 30 ? "HIGH" : impact >= 20 ? "MEDIUM" : "LOW";
        signals.add(new SignalDraft(code, signalTypeFor(code), severity, impact, description, evidence));
    }

    private void checkSanctions(MultiAssetCustomer customer, IngestTransactionRequest request,
            List<SignalDraft> signals) {
        if (sanctionsScreeningService == null) {
            return;
        }
        String screenedName = firstNonBlank(request.counterpartyReference(), customer.getDisplayName());
        if (screenedName == null || screenedName.isBlank()) {
            return;
        }
        ScreeningResult result = sanctionsScreeningService.screenName(screenedName, EntityType.PERSON);
        ScreeningStatus status = result.getStatus();
        if (status == ScreeningStatus.MATCH || status == ScreeningStatus.POTENTIAL_MATCH) {
            add(signals, "SANCTIONS_NAME_MATCH", 65,
                    "Sanctions screening returned a confirmed or potential match.",
                    Map.of("screenedName", screenedName,
                            "status", status.name(),
                            "highestScore", result.getHighestMatchScore(),
                            "matchCount", result.getMatchCount()));
            return;
        }
        if (status == ScreeningStatus.UNAVAILABLE) {
            add(signals, "SANCTIONS_SCREENING_UNAVAILABLE", 30,
                    "Sanctions screening unavailable; transaction requires review.",
                    Map.of("screenedName", screenedName));
        }
    }

    private void checkCyberIncidentContext(IngestTransactionRequest request, List<SignalDraft> signals) {
        Map<String, Object> metadata = request.metadata();
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Object incidentNumber = metadata.get("cyberIncidentNumber");
        Object incidentId = metadata.get("cyberIncidentId");
        Object confirmed = metadata.get("cyberIncidentConfirmed");
        if ((incidentNumber != null && !String.valueOf(incidentNumber).isBlank())
                || (incidentId != null && !String.valueOf(incidentId).isBlank())
                || Boolean.TRUE.equals(confirmed)) {
            Map<String, Object> evidence = new LinkedHashMap<>();
            if (incidentNumber != null) {
                evidence.put("cyberIncidentNumber", incidentNumber);
            }
            if (incidentId != null) {
                evidence.put("cyberIncidentId", incidentId);
            }
            if (confirmed != null) {
                evidence.put("cyberIncidentConfirmed", confirmed);
            }
            add(signals, "CYBER_INCIDENT_LINKED", 45,
                    "Transaction metadata references a reported cyber-security incident.",
                    evidence);
        }
    }

    private static FinancialCrimeSignalType signalTypeFor(String code) {
        if ("SECURITIES_MATCHED_ORDER".equals(code)) return FinancialCrimeSignalType.MARKET_ABUSE;
        if (code.startsWith("CRYPTO_")) return FinancialCrimeSignalType.CRYPTO_EXPOSURE;
        if (code.startsWith("SANCTIONS_")) return FinancialCrimeSignalType.SANCTIONS;
        if (code.startsWith("CYBER_")) return FinancialCrimeSignalType.CYBER;
        return FinancialCrimeSignalType.AML;
    }

    public record SignalDraft(String code, FinancialCrimeSignalType signalType, String severity, int scoreImpact,
            String description, Map<String, Object> evidence) {}

    public record Assessment(int riskScore, RiskDecision decision,
            TravelRuleStatus travelRuleStatus, List<SignalDraft> signals,
            CryptoScreeningAssessment cryptoScreening) {}

    public record CryptoScreeningAssessment(String address, String network,
            BlockchainAnalyticsClient.WalletRiskResult result) {}
}
