package com.posgateway.aml.service.mobilemoney;

import com.posgateway.aml.dto.mobilemoney.MobileMoneyDtos.*;
import com.posgateway.aml.dto.multiasset.MultiAssetDtos.*;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.mobilemoney.*;
import com.posgateway.aml.entity.multiasset.*;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.mobilemoney.*;
import com.posgateway.aml.repository.multiasset.MultiAssetRiskSignalRepository;
import com.posgateway.aml.repository.multiasset.MultiAssetTransactionRepository;
import com.posgateway.aml.service.multiasset.MultiAssetService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MobileMoneyService {
    private static final Set<MultiAssetTransactionType> SUPPORTED_TYPES = Set.of(
            MultiAssetTransactionType.DEPOSIT, MultiAssetTransactionType.WITHDRAWAL,
            MultiAssetTransactionType.TRANSFER, MultiAssetTransactionType.TOP_UP,
            MultiAssetTransactionType.PAYMENT, MultiAssetTransactionType.REFUND);

    private final MultiAssetService multiAssetService;
    private final MultiAssetTransactionRepository transactionRepository;
    private final MultiAssetRiskSignalRepository signalRepository;
    private final MobileMoneyTransactionContextRepository contextRepository;
    private final MobileMoneyNetworkEdgeRepository edgeRepository;
    private final MobileMoneyRiskProfileRepository profileRepository;
    private final MobileMoneyRiskEngine riskEngine;
    private final AlertRepository alertRepository;
    private final PspIsolationService pspIsolationService;

    public MobileMoneyService(MultiAssetService multiAssetService,
            MultiAssetTransactionRepository transactionRepository,
            MultiAssetRiskSignalRepository signalRepository,
            MobileMoneyTransactionContextRepository contextRepository,
            MobileMoneyNetworkEdgeRepository edgeRepository,
            MobileMoneyRiskProfileRepository profileRepository,
            MobileMoneyRiskEngine riskEngine,
            AlertRepository alertRepository,
            PspIsolationService pspIsolationService) {
        this.multiAssetService = multiAssetService;
        this.transactionRepository = transactionRepository;
        this.signalRepository = signalRepository;
        this.contextRepository = contextRepository;
        this.edgeRepository = edgeRepository;
        this.profileRepository = profileRepository;
        this.riskEngine = riskEngine;
        this.alertRepository = alertRepository;
        this.pspIsolationService = pspIsolationService;
    }

    @Transactional
    public MobileMoneyAssessmentResponse ingest(IngestMobileMoneyRequest request) {
        Long pspId = requiredPspId();
        validate(request);
        TransactionResponse baseResponse = multiAssetService.ingestTransaction(toMultiAssetRequest(request));
        MultiAssetTransaction transaction = transactionRepository.findByIdAndPspId(baseResponse.id(), pspId)
                .orElseThrow(() -> new IllegalStateException("Mobile-money transaction was not persisted"));

        Optional<MobileMoneyTransactionContext> existing = contextRepository
                .findByPspIdAndTransactionId(pspId, transaction.getId());
        if (existing.isPresent()) return response(transaction, existing.get());

        LocalDateTime occurredAt = request.executedAt() == null ? transaction.getExecutedAt() : request.executedAt();
        List<MobileMoneyTransactionContext> history = contextRepository
                .findTop500ByPspIdAndExecutedAtAfterOrderByExecutedAtDesc(pspId, occurredAt.minusDays(90));
        MobileMoneyRiskEngine.Assessment assessment = riskEngine.assess(request, history);

        MobileMoneyTransactionContext context = contextRepository.save(context(pspId, transaction, request, occurredAt));
        List<MultiAssetRiskSignal> mobileSignals = assessment.signals().stream()
                .map(draft -> signal(transaction, pspId, draft))
                .map(signalRepository::save)
                .toList();

        int combinedScore = Math.min(100, transaction.getRiskScore() + assessment.riskScore());
        transaction.setRiskScore(combinedScore);
        transaction.setDecision(decision(combinedScore));
        raiseCustomerRisk(transaction.getCustomer(), combinedScore);
        transactionRepository.save(transaction);
        createAlert(transaction, mobileSignals, assessment.riskScore());

        // W21-1 fix: the returned list was never used (updateNetwork persists the edges as its
        // side effect); dropped the dead capture.
        updateNetwork(pspId, transaction, context);
        updateProfiles(pspId, transaction, context, assessment.signals());
        return response(transaction, context);
    }

    @Transactional(readOnly = true)
    public Page<MobileMoneyContextResponse> listContexts(int page, int size) {
        return contextRepository.findByPspIdOrderByExecutedAtDesc(requiredPspId(),
                PageRequest.of(Math.max(page, 0), boundedSize(size))).map(this::toContextResponse);
    }

    @Transactional(readOnly = true)
    public Page<MobileMoneyRiskProfileResponse> listRiskProfiles(int page, int size) {
        return profileRepository.findByPspIdOrderByRiskScoreDescLastAssessedAtDesc(requiredPspId(),
                PageRequest.of(Math.max(page, 0), boundedSize(size))).map(this::toProfileResponse);
    }

    @Transactional(readOnly = true)
    public MobileMoneyNetworkResponse network(MobileMoneyEntityType entityType, String entityReference) {
        Long pspId = requiredPspId();
        String reference = requiredReference(entityReference, "entityReference");
        MobileMoneyRiskProfileResponse profile = profileRepository
                .findByPspIdAndEntityTypeAndEntityReference(pspId, entityType, reference)
                .map(this::toProfileResponse).orElse(null);
        Map<Long, MobileMoneyNetworkEdge> edges = new LinkedHashMap<>();
        edgeRepository.findTop200ByPspIdAndFromEntityTypeAndFromEntityReferenceOrderByLastSeenAtDesc(
                pspId, entityType, reference).forEach(edge -> edges.put(edge.getId(), edge));
        edgeRepository.findTop200ByPspIdAndToEntityTypeAndToEntityReferenceOrderByLastSeenAtDesc(
                pspId, entityType, reference).forEach(edge -> edges.put(edge.getId(), edge));
        return new MobileMoneyNetworkResponse(entityType, reference, profile,
                edges.values().stream().map(this::toEdgeResponse).toList());
    }

    private IngestTransactionRequest toMultiAssetRequest(IngestMobileMoneyRequest request) {
        Map<String, Object> metadata = sanitizedMetadata(request.metadata());
        metadata.put("mobileMoneyEventType", request.eventType().name());
        metadata.put("crossBorder", request.crossBorder());
        if (request.agentReference() != null) metadata.put("agentReference", request.agentReference());
        if (request.merchantReference() != null) metadata.put("merchantReference", request.merchantReference());
        return new IngestTransactionRequest(
                request.externalTransactionId(), request.customerId(), request.sourceAccountId(),
                request.destinationAccountId(), AssetClass.E_MONEY, request.transactionType(), request.amount(),
                request.fiatEquivalentUsd(), request.currency(), null, null, null, request.executedAt(),
                request.countryCode(), request.deviceFingerprint(), null, request.counterpartyReference(),
                request.fundingPartyCustomerId(), null, null, null, null, null, null,
                request.merchantCategory(), metadata, ProductDomain.E_MONEY_MOBILE_MONEY);
    }

    private MobileMoneyTransactionContext context(Long pspId, MultiAssetTransaction transaction,
            IngestMobileMoneyRequest request, LocalDateTime occurredAt) {
        MobileMoneyTransactionContext context = new MobileMoneyTransactionContext();
        context.setPspId(pspId);
        context.setTransaction(transaction);
        context.setWalletAccount(transaction.getSourceAccount() != null
                ? transaction.getSourceAccount() : transaction.getDestinationAccount());
        context.setWalletReference(requiredReference(request.walletReference(), "walletReference"));
        context.setCounterpartyWalletReference(reference(request.counterpartyWalletReference()));
        context.setEventType(request.eventType());
        context.setDeviceFingerprint(reference(request.deviceFingerprint()));
        context.setAgentDeviceFingerprint(reference(request.agentDeviceFingerprint()));
        context.setSimIdentifierHash(reference(request.simIdentifierHash()));
        context.setAgentReference(reference(request.agentReference()));
        context.setMerchantReference(reference(request.merchantReference()));
        context.setLatitude(request.latitude());
        context.setLongitude(request.longitude());
        context.setAgentLatitude(request.agentLatitude());
        context.setAgentLongitude(request.agentLongitude());
        context.setCellId(reference(request.cellId()));
        context.setSimChangedAt(request.simChangedAt());
        context.setDeviceRegisteredAt(request.deviceRegisteredAt());
        context.setWalletPreviousActivityAt(request.walletPreviousActivityAt());
        context.setAgentFloatBefore(request.agentFloatBefore());
        context.setAgentFloatAfter(request.agentFloatAfter());
        context.setCrossBorder(request.crossBorder());
        context.setExecutedAt(occurredAt);
        context.setMetadata(sanitizedMetadata(request.metadata()));
        return context;
    }

    private MultiAssetRiskSignal signal(MultiAssetTransaction transaction, Long pspId,
            MobileMoneyRiskEngine.SignalDraft draft) {
        MultiAssetRiskSignal signal = new MultiAssetRiskSignal();
        signal.setTransaction(transaction);
        signal.setCustomer(transaction.getCustomer());
        signal.setPspId(pspId);
        signal.setSignalCode(draft.code());
        signal.setSignalType(draft.signalType());
        signal.setProductDomain(ProductDomain.E_MONEY_MOBILE_MONEY);
        signal.setSeverity(draft.severity());
        signal.setScoreImpact(draft.scoreImpact());
        signal.setDescription(draft.description());
        signal.setEvidence(new LinkedHashMap<>(draft.evidence()));
        return signal;
    }

    private void createAlert(MultiAssetTransaction transaction, List<MultiAssetRiskSignal> signals, int mobileScore) {
        if (mobileScore < 20 || alertRepository.existsByPspIdAndSourceTypeAndSourceReference(
                transaction.getPspId(), "MOBILE_MONEY_NETWORK", transaction.getExternalTransactionId())) return;
        Alert alert = new Alert();
        alert.setPspId(transaction.getPspId());
        alert.setMultiAssetCustomerId(transaction.getCustomer().getId());
        alert.setSourceType("MOBILE_MONEY_NETWORK");
        alert.setSourceReference(transaction.getExternalTransactionId());
        alert.setScore(transaction.getRiskScore() / 100.0);
        alert.setAction(transaction.getDecision().name());
        alert.setStatus("open");
        alert.setSeverity(transaction.getRiskScore() >= 70 ? "CRITICAL"
                : transaction.getRiskScore() >= 40 ? "WARN" : "INFO");
        alert.setReason("Mobile-money network signals: " + signals.stream()
                .map(MultiAssetRiskSignal::getSignalCode).sorted().reduce((a, b) -> a + ", " + b).orElse("none"));
        alertRepository.save(alert);
    }

    private List<MobileMoneyNetworkEdge> updateNetwork(Long pspId, MultiAssetTransaction transaction,
            MobileMoneyTransactionContext context) {
        String customerReference = transaction.getCustomer().getExternalCustomerId();
        String wallet = context.getWalletReference();
        List<MobileMoneyNetworkEdge> edges = new ArrayList<>();
        edges.add(upsertEdge(pspId, transaction, MobileMoneyEntityType.CUSTOMER, customerReference,
                MobileMoneyEntityType.WALLET, wallet, MobileMoneyRelationshipType.OWNS));
        addEdge(edges, pspId, transaction, MobileMoneyEntityType.WALLET, wallet,
                MobileMoneyEntityType.DEVICE, context.getDeviceFingerprint(), MobileMoneyRelationshipType.USES_DEVICE);
        addEdge(edges, pspId, transaction, MobileMoneyEntityType.WALLET, wallet,
                MobileMoneyEntityType.SIM, context.getSimIdentifierHash(), MobileMoneyRelationshipType.REGISTERED_TO_SIM);
        addEdge(edges, pspId, transaction, MobileMoneyEntityType.WALLET, wallet,
                MobileMoneyEntityType.AGENT, context.getAgentReference(), MobileMoneyRelationshipType.SERVICED_BY_AGENT);
        addEdge(edges, pspId, transaction, MobileMoneyEntityType.AGENT, context.getAgentReference(),
                MobileMoneyEntityType.DEVICE, context.getAgentDeviceFingerprint(), MobileMoneyRelationshipType.OPERATED_ON_DEVICE);
        addEdge(edges, pspId, transaction, MobileMoneyEntityType.WALLET, wallet,
                MobileMoneyEntityType.MERCHANT, context.getMerchantReference(), MobileMoneyRelationshipType.PAID_MERCHANT);
        addEdge(edges, pspId, transaction, MobileMoneyEntityType.WALLET, wallet,
                MobileMoneyEntityType.WALLET, context.getCounterpartyWalletReference(), MobileMoneyRelationshipType.TRANSFERRED_WITH);
        if (context.getDeviceFingerprint() != null) {
            addEdge(edges, pspId, transaction, MobileMoneyEntityType.WALLET, wallet,
                    MobileMoneyEntityType.NETWORK_CLUSTER, "DEVICE:" + context.getDeviceFingerprint(),
                    MobileMoneyRelationshipType.MEMBER_OF_CLUSTER);
        }
        if (context.getSimIdentifierHash() != null) {
            addEdge(edges, pspId, transaction, MobileMoneyEntityType.WALLET, wallet,
                    MobileMoneyEntityType.NETWORK_CLUSTER, "SIM:" + context.getSimIdentifierHash(),
                    MobileMoneyRelationshipType.MEMBER_OF_CLUSTER);
        }
        return edges;
    }

    private void addEdge(List<MobileMoneyNetworkEdge> edges, Long pspId, MultiAssetTransaction transaction,
            MobileMoneyEntityType fromType, String fromReference, MobileMoneyEntityType toType,
            String toReference, MobileMoneyRelationshipType relationshipType) {
        if (fromReference == null || toReference == null) return;
        edges.add(upsertEdge(pspId, transaction, fromType, fromReference, toType, toReference, relationshipType));
    }

    private MobileMoneyNetworkEdge upsertEdge(Long pspId, MultiAssetTransaction transaction,
            MobileMoneyEntityType fromType, String fromReference, MobileMoneyEntityType toType,
            String toReference, MobileMoneyRelationshipType relationshipType) {
        MobileMoneyNetworkEdge edge = edgeRepository
                .findByPspIdAndFromEntityTypeAndFromEntityReferenceAndToEntityTypeAndToEntityReferenceAndRelationshipType(
                        pspId, fromType, fromReference, toType, toReference, relationshipType)
                .orElseGet(MobileMoneyNetworkEdge::new);
        boolean existing = edge.getId() != null;
        edge.setPspId(pspId);
        edge.setFromEntityType(fromType);
        edge.setFromEntityReference(fromReference);
        edge.setToEntityType(toType);
        edge.setToEntityReference(toReference);
        edge.setRelationshipType(relationshipType);
        edge.setOccurrenceCount(existing ? edge.getOccurrenceCount() + 1 : 1);
        edge.setCumulativeAmount((existing ? edge.getCumulativeAmount() : BigDecimal.ZERO).add(transaction.getAmount()));
        edge.setCurrency(transaction.getCurrency());
        if (!existing) edge.setFirstSeenAt(transaction.getExecutedAt());
        edge.setLastSeenAt(transaction.getExecutedAt());
        edge.setLatestTransaction(transaction);
        return edgeRepository.save(edge);
    }

    private void updateProfiles(Long pspId, MultiAssetTransaction transaction,
            MobileMoneyTransactionContext context, List<MobileMoneyRiskEngine.SignalDraft> signals) {
        Map<EntityKey, Map<String, Object>> entities = new LinkedHashMap<>();
        addEntity(entities, MobileMoneyEntityType.CUSTOMER, transaction.getCustomer().getExternalCustomerId(),
                "customerId", transaction.getCustomer().getId());
        addEntity(entities, MobileMoneyEntityType.WALLET, context.getWalletReference(), "customerId", transaction.getCustomer().getId());
        addEntity(entities, MobileMoneyEntityType.WALLET, context.getCounterpartyWalletReference(), "role", "COUNTERPARTY");
        addEntity(entities, MobileMoneyEntityType.DEVICE, context.getDeviceFingerprint(), "lastCellId", context.getCellId());
        addEntity(entities, MobileMoneyEntityType.SIM, context.getSimIdentifierHash(), "identifierStorage", "HASH_ONLY");
        addEntity(entities, MobileMoneyEntityType.AGENT, context.getAgentReference(), "lastEventType", context.getEventType());
        addEntity(entities, MobileMoneyEntityType.MERCHANT, context.getMerchantReference(), "lastEventType", context.getEventType());
        if (context.getDeviceFingerprint() != null) addEntity(entities, MobileMoneyEntityType.NETWORK_CLUSTER,
                "DEVICE:" + context.getDeviceFingerprint(), "clusterBasis", "SHARED_DEVICE");
        if (context.getSimIdentifierHash() != null) addEntity(entities, MobileMoneyEntityType.NETWORK_CLUSTER,
                "SIM:" + context.getSimIdentifierHash(), "clusterBasis", "SHARED_SIM");

        entities.forEach((key, attributes) -> upsertProfile(pspId, transaction, key, attributes,
                scoreFor(key.type(), signals), relevantSignalCount(key.type(), signals)));
    }

    private void upsertProfile(Long pspId, MultiAssetTransaction transaction, EntityKey key,
            Map<String, Object> attributes, int observedScore, int signalCount) {
        MobileMoneyRiskProfile profile = profileRepository
                .findByPspIdAndEntityTypeAndEntityReference(pspId, key.type(), key.reference())
                .orElseGet(MobileMoneyRiskProfile::new);
        boolean existing = profile.getId() != null;
        profile.setPspId(pspId);
        profile.setEntityType(key.type());
        profile.setEntityReference(key.reference());
        int score = Math.max(existing ? profile.getRiskScore() : 0, observedScore);
        profile.setRiskScore(score);
        profile.setRiskLevel(riskLevel(score));
        profile.setSignalCount((existing ? profile.getSignalCount() : 0) + signalCount);
        if (!existing) profile.setFirstSeenAt(transaction.getExecutedAt());
        profile.setLastSeenAt(transaction.getExecutedAt());
        profile.setLastAssessedAt(LocalDateTime.now());
        profile.setLatestTransaction(transaction);
        Map<String, Object> merged = existing ? new LinkedHashMap<>(profile.getAttributes()) : new LinkedHashMap<>();
        merged.putAll(attributes);
        merged.put("lastCurrency", transaction.getCurrency());
        merged.put("lastAmount", transaction.getAmount());
        profile.setAttributes(merged);
        profileRepository.save(profile);
    }

    private int scoreFor(MobileMoneyEntityType type, List<MobileMoneyRiskEngine.SignalDraft> signals) {
        return Math.min(100, signals.stream().filter(signal -> relevant(type, signal.code()))
                .mapToInt(MobileMoneyRiskEngine.SignalDraft::scoreImpact).sum());
    }

    private int relevantSignalCount(MobileMoneyEntityType type, List<MobileMoneyRiskEngine.SignalDraft> signals) {
        return (int) signals.stream().filter(signal -> relevant(type, signal.code())).count();
    }

    private boolean relevant(MobileMoneyEntityType type, String code) {
        return switch (type) {
            case CUSTOMER, WALLET -> true;
            case DEVICE -> code.contains("DEVICE") || code.contains("GEO") || code.contains("SHARED");
            case SIM -> code.contains("SIM") || code.contains("SHARED");
            case AGENT -> code.contains("AGENT");
            case MERCHANT -> code.contains("MERCHANT") || code.contains("REFUND");
            case NETWORK_CLUSTER -> code.contains("SHARED") || code.contains("CLUSTER")
                    || code.contains("CIRCULATION") || code.contains("FRAGMENTATION") || code.contains("STRUCTURING");
        };
    }

    private MobileMoneyAssessmentResponse response(MultiAssetTransaction transaction,
            MobileMoneyTransactionContext context) {
        List<MultiAssetRiskSignal> signals = signalRepository.findByTransactionIdOrderByCreatedAtAsc(transaction.getId());
        List<MobileMoneyNetworkEdge> edges = edgeRepository
                .findTop200ByPspIdAndFromEntityTypeAndFromEntityReferenceOrderByLastSeenAtDesc(
                        transaction.getPspId(), MobileMoneyEntityType.WALLET, context.getWalletReference());
        Set<EntityKey> keys = new LinkedHashSet<>();
        edges.forEach(edge -> {
            keys.add(new EntityKey(edge.getFromEntityType(), edge.getFromEntityReference()));
            keys.add(new EntityKey(edge.getToEntityType(), edge.getToEntityReference()));
        });
        List<MobileMoneyRiskProfileResponse> profiles = keys.stream()
                .map(key -> profileRepository.findByPspIdAndEntityTypeAndEntityReference(
                        transaction.getPspId(), key.type(), key.reference()))
                .flatMap(Optional::stream).map(this::toProfileResponse).toList();
        return new MobileMoneyAssessmentResponse(toTransactionResponse(transaction, signals),
                toContextResponse(context), signals.stream().map(this::toSignalResponse).toList(),
                profiles, edges.stream().map(this::toEdgeResponse).toList());
    }

    private TransactionResponse toTransactionResponse(MultiAssetTransaction transaction,
            List<MultiAssetRiskSignal> signals) {
        return new TransactionResponse(transaction.getId(), transaction.getExternalTransactionId(),
                transaction.getCustomer().getId(), transaction.getAssetClass(), transaction.getProductDomain(),
                transaction.getSourceProductDomain(), transaction.getDestinationProductDomain(),
                transaction.getTransactionType(), transaction.getAmount(), transaction.getFiatEquivalentUsd(),
                transaction.getCurrency(), transaction.getAssetSymbol(), transaction.getExecutedAt(),
                transaction.getCounterpartyReference(), transaction.getSourceNetwork(), transaction.getDestinationNetwork(),
                transaction.isTravelRuleRequired(), transaction.getTravelRuleStatus(), transaction.getRiskScore(),
                transaction.getDecision(), signals.stream().map(this::toSignalResponse).toList());
    }

    private RiskSignalResponse toSignalResponse(MultiAssetRiskSignal signal) {
        return new RiskSignalResponse(signal.getId(), signal.getTransaction().getId(), signal.getCustomer().getId(),
                signal.getSignalCode(), signal.getSignalType(), signal.getProductDomain(), signal.getSeverity(),
                signal.getScoreImpact(), signal.getDescription(), signal.getEvidence(), signal.getCreatedAt());
    }

    private MobileMoneyContextResponse toContextResponse(MobileMoneyTransactionContext context) {
        MultiAssetTransaction transaction = context.getTransaction();
        return new MobileMoneyContextResponse(context.getId(), transaction.getId(),
                transaction.getExternalTransactionId(), transaction.getCustomer().getId(),
                transaction.getCustomer().getDisplayName(), context.getWalletReference(),
                context.getCounterpartyWalletReference(), context.getEventType(), transaction.getAmount(),
                transaction.getCurrency(), context.getExecutedAt(), context.getDeviceFingerprint(),
                context.getAgentReference(), context.getMerchantReference(), context.getCellId(),
                context.isCrossBorder(), transaction.getRiskScore(), transaction.getDecision().name(), context.getMetadata());
    }

    private MobileMoneyRiskProfileResponse toProfileResponse(MobileMoneyRiskProfile profile) {
        return new MobileMoneyRiskProfileResponse(profile.getId(), profile.getEntityType(), profile.getEntityReference(),
                profile.getRiskScore(), profile.getRiskLevel(), profile.getSignalCount(), profile.getFirstSeenAt(),
                profile.getLastSeenAt(), profile.getLastAssessedAt(),
                profile.getLatestTransaction() == null ? null : profile.getLatestTransaction().getId(), profile.getAttributes());
    }

    private MobileMoneyNetworkEdgeResponse toEdgeResponse(MobileMoneyNetworkEdge edge) {
        return new MobileMoneyNetworkEdgeResponse(edge.getId(), edge.getFromEntityType(), edge.getFromEntityReference(),
                edge.getToEntityType(), edge.getToEntityReference(), edge.getRelationshipType(), edge.getOccurrenceCount(),
                edge.getCumulativeAmount(), edge.getCurrency(), edge.getFirstSeenAt(), edge.getLastSeenAt(),
                edge.getLatestTransaction() == null ? null : edge.getLatestTransaction().getId());
    }

    private void validate(IngestMobileMoneyRequest request) {
        if (!SUPPORTED_TYPES.contains(request.transactionType())) {
            throw new IllegalArgumentException("Unsupported mobile-money transaction type: " + request.transactionType());
        }
        validateEventType(request.eventType(), request.transactionType());
        validateCoordinate(request.latitude(), BigDecimal.valueOf(-90), BigDecimal.valueOf(90), "latitude");
        validateCoordinate(request.longitude(), BigDecimal.valueOf(-180), BigDecimal.valueOf(180), "longitude");
        validateCoordinate(request.agentLatitude(), BigDecimal.valueOf(-90), BigDecimal.valueOf(90), "agentLatitude");
        validateCoordinate(request.agentLongitude(), BigDecimal.valueOf(-180), BigDecimal.valueOf(180), "agentLongitude");
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new IllegalArgumentException("latitude and longitude must be supplied together");
        }
        if ((request.agentLatitude() == null) != (request.agentLongitude() == null)) {
            throw new IllegalArgumentException("agentLatitude and agentLongitude must be supplied together");
        }
        if (request.simIdentifierHash() != null && request.simIdentifierHash().length() < 16) {
            throw new IllegalArgumentException("simIdentifierHash must be a non-reversible PSP hash of at least 16 characters");
        }
        if (containsForbiddenIdentityKey(request.metadata())) {
            throw new IllegalArgumentException("Raw SIM and phone identifiers are prohibited in metadata; supply simIdentifierHash");
        }
    }

    private void validateEventType(MobileMoneyEventType eventType, MultiAssetTransactionType type) {
        boolean valid = switch (eventType) {
            case CASH_IN -> type == MultiAssetTransactionType.DEPOSIT || type == MultiAssetTransactionType.TOP_UP;
            case CASH_OUT -> type == MultiAssetTransactionType.WITHDRAWAL;
            case P2P_TRANSFER -> type == MultiAssetTransactionType.TRANSFER;
            case MERCHANT_PAYMENT, BILL_PAYMENT -> type == MultiAssetTransactionType.PAYMENT;
            case REFUND, REVERSAL -> type == MultiAssetTransactionType.REFUND;
        };
        if (!valid) throw new IllegalArgumentException("eventType is inconsistent with transactionType");
    }

    private Map<String, Object> sanitizedMetadata(Map<String, Object> metadata) {
        return metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    private boolean containsForbiddenIdentityKey(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                if (Set.of("imsi", "iccid", "msisdn", "phonenumber", "simidentifier").contains(key)
                        || containsForbiddenIdentityKey(entry.getValue())) return true;
            }
        } else if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(this::containsForbiddenIdentityKey);
        }
        return false;
    }

    private void raiseCustomerRisk(MultiAssetCustomer customer, int score) {
        String target = score >= 80 ? "CRITICAL" : score >= 60 ? "HIGH" : score >= 40 ? "MEDIUM" : customer.getRiskTier();
        if (riskRank(target) > riskRank(customer.getRiskTier())) customer.setRiskTier(target);
    }

    private int riskRank(String tier) {
        return switch (tier) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private void validateCoordinate(BigDecimal value, BigDecimal min, BigDecimal max, String field) {
        if (value != null && (value.compareTo(min) < 0 || value.compareTo(max) > 0)) {
            throw new IllegalArgumentException(field + " is outside its valid range");
        }
    }

    private void addEntity(Map<EntityKey, Map<String, Object>> entities, MobileMoneyEntityType type,
            String reference, String attribute, Object value) {
        if (reference == null) return;
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (value != null) attributes.put(attribute, value);
        entities.put(new EntityKey(type, reference), attributes);
    }

    private RiskDecision decision(int score) {
        return score >= 70 ? RiskDecision.BLOCK : score >= 40 ? RiskDecision.REVIEW
                : score >= 20 ? RiskDecision.ALERT : RiskDecision.ALLOW;
    }

    private String riskLevel(int score) {
        return score >= 70 ? "CRITICAL" : score >= 50 ? "HIGH" : score >= 20 ? "MEDIUM" : "LOW";
    }

    private int boundedSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private Long requiredPspId() {
        Long pspId = pspIsolationService.getCurrentUserPspId();
        if (pspId == null) throw new SecurityException("Authenticated user has no PSP context");
        return pspId;
    }

    private String requiredReference(String value, String field) {
        String normalized = reference(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private String reference(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 255) throw new IllegalArgumentException("Entity references cannot exceed 255 characters");
        return normalized;
    }

    private record EntityKey(MobileMoneyEntityType type, String reference) {}
}
