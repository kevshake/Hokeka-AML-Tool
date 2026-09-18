package com.posgateway.aml.service.multiasset;

import com.posgateway.aml.dto.multiasset.MultiAssetDtos.*;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.multiasset.*;
import com.posgateway.aml.entity.crypto.WalletScreeningTrigger;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.multiasset.*;
import com.posgateway.aml.service.crypto.VirtualAssetComplianceService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultiAssetService {

    private static final Set<String> CUSTOMER_TYPES = Set.of("INDIVIDUAL", "ENTITY");
    private static final Set<String> RISK_TIERS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> KYC_STATUSES = Set.of("PENDING", "VERIFIED", "REVIEW", "REJECTED");

    private final MultiAssetCustomerRepository customerRepository;
    private final AssetAccountRepository accountRepository;
    private final MultiAssetCustomerRelationshipRepository relationshipRepository;
    private final MultiAssetTransactionRepository transactionRepository;
    private final MultiAssetRiskSignalRepository signalRepository;
    private final MultiAssetRiskEngine riskEngine;
    private final PspIsolationService pspIsolationService;
    private final AlertRepository alertRepository;
    private final VirtualAssetComplianceService virtualAssetComplianceService;

    public MultiAssetService(MultiAssetCustomerRepository customerRepository,
            AssetAccountRepository accountRepository,
            MultiAssetCustomerRelationshipRepository relationshipRepository,
            MultiAssetTransactionRepository transactionRepository,
            MultiAssetRiskSignalRepository signalRepository,
            MultiAssetRiskEngine riskEngine,
            PspIsolationService pspIsolationService,
            AlertRepository alertRepository,
            VirtualAssetComplianceService virtualAssetComplianceService) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.relationshipRepository = relationshipRepository;
        this.transactionRepository = transactionRepository;
        this.signalRepository = signalRepository;
        this.riskEngine = riskEngine;
        this.pspIsolationService = pspIsolationService;
        this.alertRepository = alertRepository;
        this.virtualAssetComplianceService = virtualAssetComplianceService;
    }

    @Transactional
    public CustomerSummary createCustomer(CreateCustomerRequest request) {
        Long pspId = requiredPspId();
        customerRepository.findByPspIdAndExternalCustomerId(pspId, request.externalCustomerId().trim())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Customer already exists: " + existing.getExternalCustomerId());
                });

        MultiAssetCustomer customer = new MultiAssetCustomer();
        customer.setPspId(pspId);
        customer.setExternalCustomerId(request.externalCustomerId().trim());
        customer.setCustomerType(validated(request.customerType(), CUSTOMER_TYPES, "customerType"));
        customer.setDisplayName(request.displayName().trim());
        customer.setCountryCode(normalizeCountry(request.countryCode()));
        customer.setRiskTier(validated(defaultValue(request.riskTier(), "MEDIUM"), RISK_TIERS, "riskTier"));
        customer.setKycStatus(validated(defaultValue(request.kycStatus(), "PENDING"), KYC_STATUSES, "kycStatus"));
        return toCustomerSummary(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerSummary> listCustomers(String search, int page, int size) {
        Long pspId = requiredPspId();
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "displayName"));
        Page<MultiAssetCustomer> result = search == null || search.isBlank()
                ? customerRepository.findByPspId(pspId, pageable)
                : customerRepository.findByPspIdAndDisplayNameContainingIgnoreCase(pspId, search.trim(), pageable);
        return result.map(this::toCustomerSummary);
    }

    @Transactional
    public AccountSummary createAccount(Long customerId, CreateAssetAccountRequest request) {
        Long pspId = requiredPspId();
        MultiAssetCustomer customer = customer(customerId, pspId);
        accountRepository.findByPspIdAndExternalAccountId(pspId, request.externalAccountId().trim())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Asset account already exists: " + existing.getExternalAccountId());
                });

        AssetAccount account = new AssetAccount();
        account.setCustomer(customer);
        account.setPspId(pspId);
        account.setExternalAccountId(request.externalAccountId().trim());
        account.setAssetClass(request.assetClass());
        account.setAccountType(request.accountType());
        ProductDomain accountDomain = request.productDomain() != null
                ? request.productDomain()
                : ProductDomain.forAccountType(request.accountType(), request.assetClass());
        validateProductDomain(accountDomain, request.assetClass());
        account.setProductDomain(accountDomain);
        account.setProviderName(trimToNull(request.providerName()));
        account.setCurrency(uppercase(request.currency()));
        account.setCountryCode(normalizeCountry(request.countryCode()));
        account.setPublicAddress(trimToNull(request.publicAddress()));
        account.setMetadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : new LinkedHashMap<>());
        return toAccountSummary(accountRepository.save(account));
    }

    @Transactional
    public CustomerRelationshipResponse createRelationship(Long customerId,
            CreateCustomerRelationshipRequest request) {
        Long pspId = requiredPspId();
        MultiAssetCustomer customer = customer(customerId, pspId);
        MultiAssetCustomer related = customer(request.relatedCustomerId(), pspId);
        if (customer.getId().equals(related.getId())) {
            throw new IllegalArgumentException("A customer cannot be related to itself");
        }
        if (request.ownershipPercentage() != null
                && (request.ownershipPercentage().signum() < 0
                    || request.ownershipPercentage().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("ownershipPercentage must be between 0 and 100");
        }
        MultiAssetCustomerRelationship relationship = relationshipRepository
                .findByPspIdAndCustomerIdAndRelatedCustomerIdAndRelationshipType(
                        pspId, customerId, related.getId(), request.relationshipType())
                .orElseGet(MultiAssetCustomerRelationship::new);
        relationship.setPspId(pspId);
        relationship.setCustomer(customer);
        relationship.setRelatedCustomer(related);
        relationship.setRelationshipType(request.relationshipType());
        relationship.setOwnershipPercentage(request.ownershipPercentage());
        relationship.setMetadata(request.metadata() != null
                ? new LinkedHashMap<>(request.metadata()) : new LinkedHashMap<>());
        return toRelationshipResponse(relationshipRepository.save(relationship));
    }

    @Transactional
    public TransactionResponse ingestTransaction(IngestTransactionRequest request) {
        Long pspId = requiredPspId();
        Optional<MultiAssetTransaction> existing = transactionRepository
                .findByPspIdAndExternalTransactionId(pspId, request.externalTransactionId().trim());
        if (existing.isPresent()) {
            return toTransactionResponse(existing.get(),
                    signalRepository.findByTransactionIdOrderByCreatedAtAsc(existing.get().getId()));
        }

        MultiAssetCustomer customer = customer(request.customerId(), pspId);
        AssetAccount source = account(request.sourceAccountId(), pspId);
        AssetAccount destination = account(request.destinationAccountId(), pspId);
        validateAccountAssetClass(source, request.assetClass(), "source");
        validateAccountAssetClass(destination, request.assetClass(), "destination");
        ProductDomain productDomain = request.productDomain() != null
                ? request.productDomain()
                : source != null ? source.getProductDomain()
                : destination != null ? destination.getProductDomain()
                : ProductDomain.defaultFor(request.assetClass());
        validateProductDomain(productDomain, request.assetClass());
        ProductDomain sourceDomain = source != null ? source.getProductDomain() : productDomain;
        ProductDomain destinationDomain = destination != null ? destination.getProductDomain() : productDomain;

        List<MultiAssetTransaction> recent = transactionRepository
                .findTop500ByCustomerIdAndPspIdOrderByExecutedAtDesc(customer.getId(), pspId);
        MultiAssetRiskEngine.Assessment assessment = riskEngine.assess(customer, source, destination, request, recent);

        MultiAssetTransaction transaction = new MultiAssetTransaction();
        transaction.setPspId(pspId);
        transaction.setExternalTransactionId(request.externalTransactionId().trim());
        transaction.setCustomer(customer);
        transaction.setSourceAccount(source);
        transaction.setDestinationAccount(destination);
        transaction.setAssetClass(request.assetClass());
        transaction.setProductDomain(productDomain);
        transaction.setSourceProductDomain(sourceDomain);
        transaction.setDestinationProductDomain(destinationDomain);
        transaction.setTransactionType(request.transactionType());
        transaction.setAmount(request.amount());
        transaction.setFiatEquivalentUsd(request.fiatEquivalentUsd());
        transaction.setCurrency(uppercase(request.currency()));
        transaction.setAssetSymbol(uppercase(request.assetSymbol()));
        transaction.setQuantity(request.quantity());
        transaction.setUnitPrice(request.unitPrice());
        transaction.setExecutedAt(request.executedAt() != null ? request.executedAt() : LocalDateTime.now());
        transaction.setCountryCode(normalizeCountry(request.countryCode()));
        transaction.setDeviceFingerprint(trimToNull(request.deviceFingerprint()));
        transaction.setIpAddress(trimToNull(request.ipAddress()));
        transaction.setCounterpartyReference(trimToNull(request.counterpartyReference()));
        transaction.setFundingPartyCustomerId(trimToNull(request.fundingPartyCustomerId()));
        transaction.setSourceNetwork(trimToNull(request.sourceNetwork()));
        transaction.setDestinationNetwork(trimToNull(request.destinationNetwork()));
        // Identity payloads are persisted only by the AES-GCM protected Travel Rule workflow.
        transaction.setOriginatorName(null);
        transaction.setOriginatorAccount(null);
        transaction.setBeneficiaryName(null);
        transaction.setBeneficiaryAccount(null);
        transaction.setMerchantCategory(trimToNull(request.merchantCategory()));
        transaction.setTravelRuleStatus(assessment.travelRuleStatus());
        transaction.setTravelRuleRequired(assessment.travelRuleStatus() != TravelRuleStatus.NOT_REQUIRED);
        transaction.setRiskScore(assessment.riskScore());
        transaction.setDecision(assessment.decision());
        transaction.setMetadata(request.metadata() != null ? new LinkedHashMap<>(request.metadata()) : new LinkedHashMap<>());
        MultiAssetTransaction saved = transactionRepository.save(transaction);

        List<MultiAssetRiskSignal> signals = assessment.signals().stream()
                .map(draft -> signal(saved, customer, pspId, draft))
                .map(signalRepository::save)
                .toList();
        if (assessment.cryptoScreening() != null) {
            virtualAssetComplianceService.recordTransactionScreening(saved, assessment.cryptoScreening(),
                    screeningTrigger(request.transactionType()));
        }
        createUnifiedAlert(saved, customer, pspId, assessment, signals);
        raiseCustomerRisk(customer, assessment.riskScore());
        return toTransactionResponse(saved, signals);
    }

    @Transactional(readOnly = true)
    public Customer360Response customer360(Long customerId) {
        Long pspId = requiredPspId();
        MultiAssetCustomer customer = customer(customerId, pspId);
        List<AssetAccount> accounts = accountRepository
                .findByCustomerIdAndPspIdOrderByCreatedAtAsc(customerId, pspId);
        List<MultiAssetCustomerRelationship> relationships = relationshipRepository
                .findByCustomerIdAndPspIdOrderByCreatedAtAsc(customerId, pspId);
        List<MultiAssetTransaction> transactions = transactionRepository
                .findTop100ByCustomerIdAndPspIdOrderByExecutedAtDesc(customerId, pspId);
        List<MultiAssetRiskSignal> signals = signalRepository
                .findTop100ByCustomerIdAndPspIdOrderByCreatedAtDesc(customerId, pspId);
        Map<Long, List<MultiAssetRiskSignal>> byTransaction = signals.stream()
                .collect(Collectors.groupingBy(signal -> signal.getTransaction().getId()));

        Map<AssetClass, BigDecimal> volumeByAsset = new EnumMap<>(AssetClass.class);
        Map<AssetClass, Long> countByAsset = new EnumMap<>(AssetClass.class);
        Map<ProductDomain, BigDecimal> volumeByDomain = new EnumMap<>(ProductDomain.class);
        Map<ProductDomain, Long> countByDomain = new EnumMap<>(ProductDomain.class);
        for (MultiAssetTransaction transaction : transactions) {
            BigDecimal normalizedVolume = normalizedUsdValue(transaction);
            if (normalizedVolume != null) {
                volumeByAsset.merge(transaction.getAssetClass(), normalizedVolume, BigDecimal::add);
                volumeByDomain.merge(transaction.getProductDomain(), normalizedVolume, BigDecimal::add);
            }
            countByAsset.merge(transaction.getAssetClass(), 1L, Long::sum);
            countByDomain.merge(transaction.getProductDomain(), 1L, Long::sum);
        }

        List<Map<String, Object>> counterparties = topCounterparties(transactions);
        int compositeRisk = transactions.stream().mapToInt(MultiAssetTransaction::getRiskScore).max().orElse(0);
        return new Customer360Response(
                toCustomerSummary(customer),
                relationships.stream().map(this::toRelationshipResponse).toList(),
                accounts.stream().map(this::toAccountSummary).toList(),
                transactions.stream().map(tx -> toTransactionResponse(tx,
                        byTransaction.getOrDefault(tx.getId(), List.of()))).toList(),
                signals.stream().map(this::toSignalResponse).toList(),
                volumeByAsset,
                countByAsset,
                volumeByDomain,
                countByDomain,
                counterparties,
                compositeRisk);
    }

    @Transactional(readOnly = true)
    public Page<RiskSignalResponse> listSignals(int page, int size) {
        Long pspId = requiredPspId();
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        return signalRepository.findByPspIdOrderByCreatedAtDesc(pspId, pageable).map(this::toSignalResponse);
    }

    private MultiAssetRiskSignal signal(MultiAssetTransaction transaction, MultiAssetCustomer customer,
            Long pspId, MultiAssetRiskEngine.SignalDraft draft) {
        MultiAssetRiskSignal signal = new MultiAssetRiskSignal();
        signal.setTransaction(transaction);
        signal.setCustomer(customer);
        signal.setPspId(pspId);
        signal.setSignalCode(draft.code());
        signal.setSignalType(draft.signalType());
        signal.setProductDomain(transaction.getProductDomain());
        signal.setSeverity(draft.severity());
        signal.setScoreImpact(draft.scoreImpact());
        signal.setDescription(draft.description());
        signal.setEvidence(new LinkedHashMap<>(draft.evidence()));
        return signal;
    }

    private WalletScreeningTrigger screeningTrigger(MultiAssetTransactionType transactionType) {
        return switch (transactionType) {
            case WITHDRAWAL, TRANSFER, BRIDGE, SWAP -> WalletScreeningTrigger.PRE_WITHDRAWAL;
            case DEPOSIT -> WalletScreeningTrigger.DEPOSIT;
            default -> WalletScreeningTrigger.MANUAL;
        };
    }

    private void createUnifiedAlert(MultiAssetTransaction transaction, MultiAssetCustomer customer,
            Long pspId, MultiAssetRiskEngine.Assessment assessment, List<MultiAssetRiskSignal> signals) {
        if (assessment.decision() == RiskDecision.ALLOW
                || alertRepository.existsByPspIdAndSourceTypeAndSourceReference(
                        pspId, "MULTI_ASSET_TRANSACTION", transaction.getExternalTransactionId())) {
            return;
        }
        Alert alert = new Alert();
        alert.setPspId(pspId);
        alert.setMultiAssetCustomerId(customer.getId());
        alert.setSourceType("MULTI_ASSET_TRANSACTION");
        alert.setSourceReference(transaction.getExternalTransactionId());
        alert.setScore(assessment.riskScore() / 100.0);
        alert.setAction(assessment.decision().name());
        alert.setStatus("open");
        alert.setSeverity(assessment.riskScore() >= 70 ? "CRITICAL"
                : assessment.riskScore() >= 40 ? "WARN" : "INFO");
        String signalSummary = signals.stream()
                .map(signal -> signal.getSignalCode() + ": " + signal.getDescription())
                .collect(Collectors.joining("; "));
        alert.setReason("Multi-asset customer " + customer.getExternalCustomerId()
                + " / " + transaction.getAssetClass() + " / " + signalSummary);
        alertRepository.save(alert);
    }

    private void raiseCustomerRisk(MultiAssetCustomer customer, int score) {
        String current = customer.getRiskTier();
        String target = score >= 80 ? "CRITICAL" : score >= 60 ? "HIGH" : score >= 40 ? "MEDIUM" : current;
        if (riskRank(target) > riskRank(current)) {
            customer.setRiskTier(target);
            customerRepository.save(customer);
        }
    }

    private int riskRank(String tier) {
        return switch (tier) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private List<Map<String, Object>> topCounterparties(List<MultiAssetTransaction> transactions) {
        Map<String, BigDecimal> totals = new HashMap<>();
        transactions.stream().filter(tx -> tx.getCounterpartyReference() != null)
                .filter(tx -> normalizedUsdValue(tx) != null)
                .forEach(tx -> totals.merge(tx.getCounterpartyReference(), normalizedUsdValue(tx), BigDecimal::add));
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("counterpartyReference", entry.getKey());
                    value.put("volume", entry.getValue());
                    return value;
                }).toList();
    }

    private MultiAssetCustomer customer(Long id, Long pspId) {
        return customerRepository.findByIdAndPspId(id, pspId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    private AssetAccount account(Long id, Long pspId) {
        if (id == null) return null;
        return accountRepository.findByIdAndPspId(id, pspId)
                .orElseThrow(() -> new IllegalArgumentException("Asset account not found: " + id));
    }

    private void validateAccountAssetClass(AssetAccount account, AssetClass assetClass, String label) {
        if (account != null && account.getAssetClass() != assetClass) {
            throw new IllegalArgumentException(label + " account asset class does not match the transaction");
        }
    }

    private void validateProductDomain(ProductDomain productDomain, AssetClass assetClass) {
        if (productDomain == null || !productDomain.supports(assetClass)) {
            throw new IllegalArgumentException("productDomain does not belong to assetClass " + assetClass);
        }
    }

    private Long requiredPspId() {
        Long pspId = pspIsolationService.getCurrentUserPspId();
        if (pspId == null) throw new SecurityException("Authenticated user has no PSP context");
        return pspId;
    }

    private CustomerSummary toCustomerSummary(MultiAssetCustomer customer) {
        return new CustomerSummary(customer.getId(), customer.getExternalCustomerId(), customer.getCustomerType(),
                customer.getDisplayName(), customer.getCountryCode(), customer.getRiskTier(), customer.getKycStatus(),
                customer.getLastReviewedAt(), customer.getCreatedAt());
    }

    private AccountSummary toAccountSummary(AssetAccount account) {
        return new AccountSummary(account.getId(), account.getExternalAccountId(), account.getAssetClass(),
                account.getProductDomain(), account.getAccountType(), account.getProviderName(), account.getCurrency(), account.getCountryCode(),
                account.getPublicAddress(), account.getStatus(), account.getMetadata());
    }

    private CustomerRelationshipResponse toRelationshipResponse(MultiAssetCustomerRelationship relationship) {
        MultiAssetCustomer related = relationship.getRelatedCustomer();
        return new CustomerRelationshipResponse(relationship.getId(), related.getId(),
                related.getExternalCustomerId(), related.getDisplayName(), relationship.getRelationshipType(),
                relationship.getOwnershipPercentage(), relationship.getMetadata(), relationship.getCreatedAt());
    }

    private TransactionResponse toTransactionResponse(MultiAssetTransaction transaction,
            List<MultiAssetRiskSignal> signals) {
        return new TransactionResponse(transaction.getId(), transaction.getExternalTransactionId(),
                transaction.getCustomer().getId(), transaction.getAssetClass(), transaction.getProductDomain(),
                transaction.getSourceProductDomain(), transaction.getDestinationProductDomain(), transaction.getTransactionType(),
                transaction.getAmount(), transaction.getFiatEquivalentUsd(), transaction.getCurrency(),
                transaction.getAssetSymbol(), transaction.getExecutedAt(), transaction.getCounterpartyReference(),
                transaction.getSourceNetwork(), transaction.getDestinationNetwork(), transaction.isTravelRuleRequired(),
                transaction.getTravelRuleStatus(), transaction.getRiskScore(), transaction.getDecision(),
                signals.stream().map(this::toSignalResponse).toList());
    }

    private RiskSignalResponse toSignalResponse(MultiAssetRiskSignal signal) {
        return new RiskSignalResponse(signal.getId(), signal.getTransaction().getId(), signal.getCustomer().getId(),
                signal.getSignalCode(), signal.getSignalType(), signal.getProductDomain(), signal.getSeverity(),
                signal.getScoreImpact(), signal.getDescription(),
                signal.getEvidence(), signal.getCreatedAt());
    }

    private static String validated(String value, Set<String> allowed, String field) {
        String normalized = uppercase(value);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(field + " must be one of " + allowed);
        }
        return normalized;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizeCountry(String value) {
        String normalized = uppercase(value);
        if (normalized != null && (normalized.length() < 2 || normalized.length() > 3)) {
            throw new IllegalArgumentException("countryCode must contain 2 or 3 characters");
        }
        return normalized;
    }

    private static String uppercase(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal normalizedUsdValue(MultiAssetTransaction transaction) {
        if (transaction.getFiatEquivalentUsd() != null) return transaction.getFiatEquivalentUsd();
        return Set.of("USD", "USDT", "USDC").contains(transaction.getCurrency())
                ? transaction.getAmount() : null;
    }
}
