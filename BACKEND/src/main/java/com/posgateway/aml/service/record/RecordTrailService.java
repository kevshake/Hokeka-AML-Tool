package com.posgateway.aml.service.record;

import com.posgateway.aml.dto.record.RecordTrailDtos.*;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.TransactionFeatures;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.corporate.CorporateIntelligenceCheck;
import com.posgateway.aml.entity.edd.EddEvidenceEvent;
import com.posgateway.aml.entity.edd.EnhancedDueDiligenceRequest;
import com.posgateway.aml.entity.crypto.CryptoWalletProfile;
import com.posgateway.aml.entity.crypto.TravelRuleJurisdictionPolicy;
import com.posgateway.aml.entity.crypto.TravelRuleTransmissionAttempt;
import com.posgateway.aml.entity.crypto.TravelRuleTransfer;
import com.posgateway.aml.entity.crypto.VaspDirectoryEntry;
import com.posgateway.aml.entity.crypto.VaspScreeningRecord;
import com.posgateway.aml.entity.crypto.VirtualAssetRegulatorAccessGrant;
import com.posgateway.aml.entity.crypto.VirtualAssetRegulatorAccessLog;
import com.posgateway.aml.entity.crypto.WalletScreeningRecord;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.merchant.BeneficialOwner;
import com.posgateway.aml.entity.merchant.MerchantDocument;
import com.posgateway.aml.entity.integration.FixMessageEvent;
import com.posgateway.aml.entity.market.MarketExecution;
import com.posgateway.aml.entity.market.MarketOrder;
import com.posgateway.aml.entity.market.MarketSurveillanceSignal;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyRiskProfile;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyNetworkEdge;
import com.posgateway.aml.entity.mobilemoney.MobileMoneyTransactionContext;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomerRelationship;
import com.posgateway.aml.entity.multiasset.MultiAssetRiskSignal;
import com.posgateway.aml.entity.multiasset.MultiAssetTransaction;
import com.posgateway.aml.entity.reporting.ReportExecution;
import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import com.posgateway.aml.entity.reporting.RegulatorySubmissionAttempt;
import com.posgateway.aml.entity.compliance.CbkSubmission;
import com.posgateway.aml.service.security.PspIsolationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.UUID;

/**
 * Resolves records into an access-controlled graph. The same DTO is used by record detail
 * screens, report results, and export provenance so a user can follow any supported trail.
 */
@Service
@Transactional(readOnly = true)
public class RecordTrailService {
    private static final int RELATED_LIMIT = 100;
    private static final int GENERIC_RELATED_LIMIT = 50;
    private static final Set<String> SENSITIVE_FIELD_PARTS = Set.of(
            "password", "secret", "token", "apikey", "api_key", "hash", "rawpayload", "raw_payload",
            "filepath", "file_path", "attachmentpath", "attachment_path", "privatekey", "private_key",
            "payload", "accesskey", "passport", "nationalid", "national_id");
    private static final Set<String> SAFE_INTEGRITY_HASH_FIELDS = Set.of(
            "evidencehash", "messagehash", "contenthash", "sha256hash", "checksum",
            "queryhash", "payloadhash", "requestsha256", "responsesha256");
    private static final Set<String> SHARED_REFERENCE_TYPES = Set.of(
            "REPORT", "REPORT_DEFINITION", "RULE_DEFINITION", "VELOCITY_RULE", "RISK_THRESHOLD",
            "ROLE", "SKILL_TYPE", "PRICING_TIER", "HIGH_RISK_COUNTRY", "SANCTIONS_LIST");
    private static final Map<String, String> FOREIGN_KEY_TYPES = Map.ofEntries(
            Map.entry("merchantid", "MERCHANT"),
            Map.entry("alertid", "ALERT"),
            Map.entry("caseid", "CASE"),
            Map.entry("compliancecaseid", "CASE"),
            Map.entry("transactionid", "TRANSACTION"),
            Map.entry("txnid", "TRANSACTION"),
            Map.entry("pspid", "PSP"),
            Map.entry("userid", "USER"),
            Map.entry("createdby", "USER"),
            Map.entry("updatedby", "USER"),
            Map.entry("triggeredby", "USER"),
            Map.entry("preparedby", "USER"),
            Map.entry("reviewedby", "USER"),
            Map.entry("approvedby", "USER"),
            Map.entry("filedby", "USER"),
            Map.entry("reportid", "REPORT"),
            Map.entry("scheduleid", "REPORT_SCHEDULE"),
            Map.entry("executionid", "REPORT_EXECUTION"),
            Map.entry("reportexecutionid", "REPORT_EXECUTION"),
            Map.entry("invoiceid", "INVOICE"),
            Map.entry("subscriptionid", "SUBSCRIPTION"),
            Map.entry("disputeid", "CHARGEBACK_DISPUTE"),
            Map.entry("chargebackdisputeid", "CHARGEBACK_DISPUTE"),
            Map.entry("regulatorysubmissionid", "REGULATORY_SUBMISSION"),
            Map.entry("cbksubmissionid", "CBK_SUBMISSION"),
            Map.entry("roleid", "ROLE"),
            Map.entry("eddrequestid", "ENHANCED_DUE_DILIGENCE_REQUEST"),
            Map.entry("marketorderid", "MARKET_ORDER"),
            Map.entry("marketexecutionid", "MARKET_EXECUTION"),
            Map.entry("previousversionid", "MERCHANT_DOCUMENT"));

    private final EntityManager entityManager;
    private final PspIsolationService pspIsolationService;

    public RecordTrailService(EntityManager entityManager, PspIsolationService pspIsolationService) {
        this.entityManager = entityManager;
        this.pspIsolationService = pspIsolationService;
    }

    public RecordDetail getDetail(String type, String id) {
        String recordType = normalizeType(type);
        RecordType supportedType;
        try {
            supportedType = RecordType.valueOf(recordType);
        } catch (IllegalArgumentException ignored) {
            return genericDetail(recordType, id);
        }
        return switch (supportedType) {
            case ALERT -> alertDetail(parseId(id));
            case CASE -> caseDetail(parseId(id));
            case TRANSACTION -> transactionDetail(parseId(id));
            case MERCHANT -> merchantDetail(parseId(id));
            case MULTI_ASSET_CUSTOMER -> customerDetail(parseId(id));
            case MULTI_ASSET_TRANSACTION -> multiAssetTransactionDetail(parseId(id));
            case REPORT_EXECUTION -> reportExecutionDetail(id);
        };
    }

    /** Report rows can call this to turn source identifiers into navigable records. */
    public Optional<RecordLink> resolveLink(String type, Object id) {
        if (type == null || id == null) return Optional.empty();
        try {
            RecordDetail detail = getDetail(type, String.valueOf(id));
            return Optional.of(link(detail.recordType(), detail.recordId(), detail.title(), "SOURCE_RECORD", detail.data()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private RecordDetail alertDetail(Long id) {
        Alert alert = find(Alert.class, id, "Alert");
        Long pspId = alert.getPspId() != null ? alert.getPspId() : merchantPspId(alert.getMerchantId());
        requireScope(pspId);
        Map<String, Object> data = values(
                "alertId", alert.getAlertId(), "action", alert.getAction(), "score", alert.getScore(),
                "severity", alert.getSeverity(), "status", alert.getStatus(), "reason", alert.getReason(),
                "sourceType", alert.getSourceType(), "sourceReference", alert.getSourceReference(),
                "createdAt", alert.getCreatedAt(), "disposition", alert.getDisposition(),
                "dispositionReason", alert.getDispositionReason(), "pspId", pspId);
        List<RecordLink> links = new ArrayList<>();
        if (alert.getMerchantId() != null) merchant(alert.getMerchantId()).ifPresent(m -> links.add(merchantLink(m, "MERCHANT")));
        if (alert.getTxnId() != null) transaction(alert.getTxnId()).ifPresent(t -> links.add(transactionLink(t, "TRIGGERED_BY_TRANSACTION")));
        if (alert.getMultiAssetCustomerId() != null) customer(alert.getMultiAssetCustomerId())
                .ifPresent(c -> links.add(customerLink(c, "MULTI_ASSET_CUSTOMER")));
        if ("MARKET_SURVEILLANCE".equals(alert.getSourceType()) && alert.getSourceReference() != null) {
            referenceLink("MARKET_SURVEILLANCE_SIGNAL", alert.getSourceReference(), "SOURCE_SIGNAL")
                    .ifPresent(links::add);
        }
        if ("CORPORATE_INTELLIGENCE".equals(alert.getSourceType()) && alert.getSourceReference() != null) {
            referenceLink("CORPORATE_INTELLIGENCE_CHECK", alert.getSourceReference(), "SOURCE_CHECK")
                    .ifPresent(links::add);
        }
        if ("MOBILE_MONEY_NETWORK".equals(alert.getSourceType()) && alert.getSourceReference() != null) {
            entityManager.createQuery(
                    "select t from MultiAssetTransaction t where t.pspId = :pspId and t.externalTransactionId = :reference",
                    MultiAssetTransaction.class)
                    .setParameter("pspId", pspId)
                    .setParameter("reference", alert.getSourceReference())
                    .getResultStream().findFirst().ifPresent(transaction -> {
                        links.add(multiAssetTransactionLink(transaction, "SOURCE_TRANSACTION"));
                        entityManager.createQuery(
                                "select c from MobileMoneyTransactionContext c where c.transaction.id = :transactionId",
                                MobileMoneyTransactionContext.class)
                                .setParameter("transactionId", transaction.getId())
                                .getResultStream().findFirst()
                                .flatMap(context -> genericLink(context, "MOBILE_MONEY_CONTEXT"))
                                .ifPresent(links::add);
                    });
        }
        if ("CRYPTO_WALLET_SCREENING".equals(alert.getSourceType()) && alert.getSourceReference() != null) {
            referenceLink("WALLET_SCREENING_RECORD", alert.getSourceReference(), "SOURCE_SCREENING")
                    .ifPresent(links::add);
        }
        if ("VASP_SANCTIONS_SCREENING".equals(alert.getSourceType()) && alert.getSourceReference() != null) {
            referenceLink("VASP_DIRECTORY_ENTRY", alert.getSourceReference(), "SOURCE_VASP").ifPresent(links::add);
        }
        return detail(RecordType.ALERT, id, "Alert #" + id, data, links,
                List.of(occurrence("ALERT_CREATED", alert.getCreatedAt(), alert.getReason(), data, null)));
    }

    private RecordDetail caseDetail(Long id) {
        ComplianceCase complianceCase = find(ComplianceCase.class, id, "Case");
        requireScope(complianceCase.getPspId());
        Map<String, Object> data = values(
                "caseReference", complianceCase.getCaseReference(), "status", complianceCase.getStatus(),
                "priority", complianceCase.getPriority(), "description", complianceCase.getDescription(),
                "slaDeadline", complianceCase.getSlaDeadline(), "createdAt", complianceCase.getCreatedAt(),
                "updatedAt", complianceCase.getUpdatedAt(), "merchantId", complianceCase.getMerchantId(),
                "pspId", complianceCase.getPspId());
        List<RecordLink> links = new ArrayList<>();
        if (complianceCase.getMerchantId() != null) merchant(complianceCase.getMerchantId())
                .ifPresent(m -> links.add(merchantLink(m, "SUBJECT_MERCHANT")));
        List<RecordOccurrence> occurrences = new ArrayList<>();
        occurrences.add(occurrence("CASE_CREATED", complianceCase.getCreatedAt(), complianceCase.getDescription(), data, null));
        if (complianceCase.getAlerts() != null) {
            complianceCase.getAlerts().forEach(caseAlert -> occurrences.add(occurrence("CASE_ALERT", caseAlert.getTriggeredAt(),
                    caseAlert.getDescription(), values("alertType", caseAlert.getAlertType(), "score", caseAlert.getScore()), null)));
        }
        return detail(RecordType.CASE, id, complianceCase.getCaseReference(), data, links, occurrences);
    }

    private RecordDetail transactionDetail(Long id) {
        TransactionEntity transaction = find(TransactionEntity.class, id, "Transaction");
        requireScope(transaction.getPspId());
        TransactionFeatures scoringEvidence = entityManager.find(TransactionFeatures.class, id);
        Map<String, Object> data = values(
                "transactionId", transaction.getTxnId(), "merchantId", transaction.getMerchantId(), "pspId", transaction.getPspId(),
                "amountCents", transaction.getAmountCents(), "currency", transaction.getCurrency(), "timestamp", transaction.getTxnTs(),
                "decision", transaction.getDecision(), "riskLevel", transaction.getRiskLevel(), "krs", transaction.getKrs(),
                "trs", transaction.getTrs(), "cra", transaction.getCra(), "direction", transaction.getDirection(),
                "merchantCountry", transaction.getMerchantCountry(), "terminalId", transaction.getTerminalId(),
                "channelType", transaction.getChannelType(), "cardBrand", transaction.getCardBrand(),
                "cardType", transaction.getCardType(), "cardClass", transaction.getCardClass(),
                "billClassificationCode", transaction.getBillClassificationCode(),
                "cashTransaction", transaction.isCashTransaction(),
                "sarRequired", transaction.isSarRequired(), "ctrRequired", transaction.isCtrRequired(),
                "ctrEvaluationStatus", transaction.getCtrEvaluationStatus(),
                "ctrUsdEquivalent", transaction.getCtrUsdEquivalent(),
                "ctrThresholdUsd", transaction.getCtrThresholdUsd(),
                "ctrRateSource", transaction.getCtrRateSource(),
                "ctrRateEffectiveAt", transaction.getCtrRateEffectiveAt(),
                "ctrEvaluatedAt", transaction.getCtrEvaluatedAt(),
                "ruleDecision", transaction.getRuleDecision(), "triggeredRules", transaction.getTriggeredRules(),
                "score", scoringEvidence != null ? scoringEvidence.getScore() : null,
                "actionTaken", scoringEvidence != null ? scoringEvidence.getActionTaken() : null,
                "modelVersion", scoringEvidence != null ? scoringEvidence.getModelVersion() : null,
                "scoringLatencyMs", scoringEvidence != null ? scoringEvidence.getLatencyMs() : null,
                "featureInputs", scoringEvidence != null ? scoringEvidence.getFeatureJson() : null,
                "riskDetails", scoringEvidence != null ? scoringEvidence.getRiskDetails() : null);
        List<RecordLink> links = new ArrayList<>();
        merchantFromTransaction(transaction).ifPresent(m -> links.add(merchantLink(m, "MERCHANT")));
        List<Alert> alerts = entityManager.createQuery("select a from Alert a where a.txnId = :id order by a.createdAt desc", Alert.class)
                .setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        alerts.forEach(a -> links.add(alertLink(a, "GENERATED_ALERT")));
        List<RecordOccurrence> occurrences = new ArrayList<>();
        occurrences.add(occurrence("TRANSACTION_RECORDED", transaction.getTxnTs(), "Transaction recorded", data, null));
        if (scoringEvidence != null) {
            occurrences.add(occurrence("TRANSACTION_SCORED", scoringEvidence.getScoredAt(),
                    "Transaction scored as " + scoringEvidence.getActionTaken(),
                    values("score", scoringEvidence.getScore(), "modelVersion", scoringEvidence.getModelVersion(),
                            "latencyMs", scoringEvidence.getLatencyMs(), "riskDetails", scoringEvidence.getRiskDetails()), null));
        }
        alerts.forEach(a -> occurrences.add(occurrence("ALERT", a.getCreatedAt(), a.getReason(), values("severity", a.getSeverity(), "action", a.getAction()), alertLink(a, "GENERATED_ALERT"))));
        return detail(RecordType.TRANSACTION, id, "Transaction #" + id, data, links, occurrences);
    }

    private RecordDetail merchantDetail(Long id) {
        Merchant merchant = find(Merchant.class, id, "Merchant");
        Long pspId = merchant.getPsp() == null ? null : merchant.getPsp().getPspId();
        requireScope(pspId);
        Map<String, Object> data = values(
                "merchantId", merchant.getMerchantId(), "legalName", merchant.getLegalName(), "tradingName", merchant.getTradingName(),
                "country", merchant.getCountry(), "mcc", merchant.getMcc(), "status", merchant.getStatus(),
                "riskLevel", merchant.getRiskLevel(), "krs", merchant.getKrs(), "pspId", pspId,
                "createdAt", merchant.getCreatedAt(), "lastScreenedAt", merchant.getLastScreenedAt());
        List<RecordLink> links = new ArrayList<>();
        List<TransactionEntity> transactions = entityManager.createQuery("select t from TransactionEntity t where t.merchantId = :merchantId order by t.txnTs desc", TransactionEntity.class)
                .setParameter("merchantId", String.valueOf(id)).setMaxResults(RELATED_LIMIT).getResultList();
        transactions.forEach(t -> links.add(transactionLink(t, "TRANSACTION")));
        List<Alert> alerts = entityManager.createQuery("select a from Alert a where a.merchantId = :merchantId order by a.createdAt desc", Alert.class)
                .setParameter("merchantId", id).setMaxResults(RELATED_LIMIT).getResultList();
        alerts.forEach(a -> links.add(alertLink(a, "ALERT")));
        List<ComplianceCase> cases = entityManager.createQuery("select c from ComplianceCase c where c.merchantId = :merchantId order by c.createdAt desc", ComplianceCase.class)
                .setParameter("merchantId", id).setMaxResults(RELATED_LIMIT).getResultList();
        cases.forEach(c -> links.add(caseLink(c, "CASE")));
        List<BeneficialOwner> owners = entityManager.createQuery(
                "select o from BeneficialOwner o where o.merchant.merchantId = :merchantId order by o.createdAt desc",
                BeneficialOwner.class).setParameter("merchantId", id).setMaxResults(RELATED_LIMIT).getResultList();
        owners.forEach(owner -> genericLink(owner, owner.getOwnershipPercentage() != null
                && owner.getOwnershipPercentage() >= 25 ? "ULTIMATE_BENEFICIAL_OWNER" : "BENEFICIAL_OWNER")
                .ifPresent(links::add));
        List<MerchantDocument> documents = entityManager.createQuery(
                "select d from MerchantDocument d where d.merchantId = :merchantId order by d.uploadedAt desc",
                MerchantDocument.class).setParameter("merchantId", id).setMaxResults(RELATED_LIMIT).getResultList();
        documents.forEach(document -> genericLink(document, "KYC_DOCUMENT").ifPresent(links::add));
        List<EnhancedDueDiligenceRequest> eddRequests = entityManager.createQuery(
                "select e from EnhancedDueDiligenceRequest e where e.merchantId = :merchantId",
                EnhancedDueDiligenceRequest.class).setParameter("merchantId", id).getResultList();
        eddRequests.forEach(edd -> {
            genericLink(edd, "ENHANCED_DUE_DILIGENCE").ifPresent(links::add);
            entityManager.createQuery(
                    "select event from EddEvidenceEvent event where event.eddRequestId = :requestId order by event.occurredAt desc",
                    EddEvidenceEvent.class).setParameter("requestId", edd.getId()).setMaxResults(RELATED_LIMIT)
                    .getResultList().forEach(event -> genericLink(event, "EDD_EVIDENCE_EVENT").ifPresent(links::add));
        });
        List<CorporateIntelligenceCheck> intelligenceChecks = entityManager.createQuery(
                "select c from CorporateIntelligenceCheck c where c.merchant.merchantId = :merchantId order by c.checkedAt desc",
                CorporateIntelligenceCheck.class).setParameter("merchantId", id)
                .setMaxResults(RELATED_LIMIT).getResultList();
        intelligenceChecks.forEach(check -> genericLink(check, "CORPORATE_INTELLIGENCE").ifPresent(links::add));
        List<RecordOccurrence> occurrences = new ArrayList<>();
        occurrences.add(occurrence("MERCHANT_CREATED", merchant.getCreatedAt(), "Merchant onboarding record", data, null));
        alerts.forEach(a -> occurrences.add(occurrence("ALERT", a.getCreatedAt(), a.getReason(), values("severity", a.getSeverity()), alertLink(a, "ALERT"))));
        documents.forEach(document -> occurrences.add(occurrence("KYC_DOCUMENT_" + document.getStatus(),
                document.getVerifiedAt() != null ? document.getVerifiedAt() : document.getUploadedAt(),
                document.getDocumentType(), values("expiryDate", document.getExpiryDate(),
                        "malwareScanStatus", document.getMalwareScanStatus(), "verifiedBy", document.getVerifiedBy()),
                genericLink(document, "KYC_DOCUMENT").orElse(null))));
        eddRequests.forEach(edd -> occurrences.add(occurrence("EDD_" + edd.getStatus(),
                edd.getCompletedAt() != null ? edd.getCompletedAt() : edd.getInitiatedAt(),
                "Enhanced due diligence", values("initiatedBy", edd.getInitiatedBy(),
                        "completedBy", edd.getCompletedBy()), genericLink(edd, "ENHANCED_DUE_DILIGENCE").orElse(null))));
        intelligenceChecks.forEach(check -> occurrences.add(occurrence(
                "CORPORATE_INTELLIGENCE_" + check.getStatus(), check.getCheckedAt(),
                check.getDecisionReason(), values("registryStatus", check.getRegistryStatus(),
                        "adverseMediaStatus", check.getAdverseMediaStatus(), "riskScore", check.getRiskScore(),
                        "evidenceHash", check.getEvidenceHash()),
                genericLink(check, "CORPORATE_INTELLIGENCE").orElse(null))));
        return detail(RecordType.MERCHANT, id, merchant.getLegalName(), data, links, occurrences);
    }

    private RecordDetail customerDetail(Long id) {
        MultiAssetCustomer customer = find(MultiAssetCustomer.class, id, "Multi-asset customer");
        requireScope(customer.getPspId());
        Map<String, Object> data = values(
                "customerId", customer.getId(), "externalCustomerId", customer.getExternalCustomerId(),
                "displayName", customer.getDisplayName(), "customerType", customer.getCustomerType(),
                "countryCode", customer.getCountryCode(), "riskTier", customer.getRiskTier(),
                "kycStatus", customer.getKycStatus(), "pspId", customer.getPspId(), "createdAt", customer.getCreatedAt());
        List<RecordLink> links = new ArrayList<>();
        List<MultiAssetCustomerRelationship> relationships = entityManager.createQuery(
                "select r from MultiAssetCustomerRelationship r where r.customer.id = :id or r.relatedCustomer.id = :id order by r.createdAt desc",
                MultiAssetCustomerRelationship.class).setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        relationships.forEach(r -> {
            MultiAssetCustomer other = r.getCustomer().getId().equals(id) ? r.getRelatedCustomer() : r.getCustomer();
            links.add(customerLink(other, r.getRelationshipType().name()));
        });
        List<MultiAssetTransaction> transactions = entityManager.createQuery(
                "select t from MultiAssetTransaction t where t.customer.id = :id order by t.executedAt desc", MultiAssetTransaction.class)
                .setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        transactions.forEach(t -> links.add(multiAssetTransactionLink(t, "ACTIVITY")));
        List<Alert> alerts = entityManager.createQuery("select a from Alert a where a.multiAssetCustomerId = :id order by a.createdAt desc", Alert.class)
                .setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        alerts.forEach(a -> links.add(alertLink(a, "ALERT")));
        List<MarketOrder> marketOrders = entityManager.createQuery(
                "select o from MarketOrder o where o.customer.id = :id order by o.placedAt desc", MarketOrder.class)
                .setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        marketOrders.forEach(order -> genericLink(order, "MARKET_ORDER").ifPresent(links::add));
        List<MarketSurveillanceSignal> marketSignals = entityManager.createQuery(
                "select s from MarketSurveillanceSignal s where s.customer.id = :id order by s.createdAt desc",
                MarketSurveillanceSignal.class).setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        marketSignals.forEach(signal -> genericLink(signal, "MARKET_ABUSE_SIGNAL").ifPresent(links::add));
        List<MobileMoneyTransactionContext> mobileMoneyContexts = entityManager.createQuery(
                "select c from MobileMoneyTransactionContext c where c.transaction.customer.id = :id order by c.executedAt desc",
                MobileMoneyTransactionContext.class).setParameter("id", id)
                .setMaxResults(RELATED_LIMIT).getResultList();
        mobileMoneyContexts.forEach(context -> genericLink(context, "MOBILE_MONEY_ACTIVITY").ifPresent(links::add));
        List<MobileMoneyRiskProfile> mobileMoneyProfiles = entityManager.createQuery(
                "select p from MobileMoneyRiskProfile p where p.pspId = :pspId and p.entityType = com.posgateway.aml.entity.mobilemoney.MobileMoneyEntityType.CUSTOMER and p.entityReference = :reference",
                MobileMoneyRiskProfile.class).setParameter("pspId", customer.getPspId())
                .setParameter("reference", customer.getExternalCustomerId()).getResultList();
        mobileMoneyProfiles.forEach(profile -> genericLink(profile, "MOBILE_MONEY_RISK_PROFILE").ifPresent(links::add));
        List<CryptoWalletProfile> cryptoWallets = entityManager.createQuery(
                "select w from CryptoWalletProfile w where w.customer.id = :id order by w.updatedAt desc",
                CryptoWalletProfile.class).setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        cryptoWallets.forEach(wallet -> genericLink(wallet, "CRYPTO_WALLET").ifPresent(links::add));
        List<RecordOccurrence> occurrences = new ArrayList<>();
        occurrences.add(occurrence("CUSTOMER_CREATED", customer.getCreatedAt(), "Customer profile created", data, null));
        transactions.forEach(t -> occurrences.add(occurrence("MULTI_ASSET_ACTIVITY", t.getExecutedAt(), t.getTransactionType().name(),
                values("assetClass", t.getAssetClass(), "riskScore", t.getRiskScore(), "decision", t.getDecision()), multiAssetTransactionLink(t, "ACTIVITY"))));
        marketSignals.forEach(signal -> occurrences.add(occurrence("MARKET_ABUSE_SIGNAL", signal.getCreatedAt(),
                signal.getDescription(), values("scenarioCode", signal.getScenarioCode(), "severity", signal.getSeverity(),
                        "score", signal.getScore()), genericLink(signal, "MARKET_ABUSE_SIGNAL").orElse(null))));
        mobileMoneyContexts.forEach(context -> occurrences.add(occurrence("MOBILE_MONEY_ACTIVITY", context.getExecutedAt(),
                context.getEventType().name(), values("walletReference", context.getWalletReference(),
                        "agentReference", context.getAgentReference(), "merchantReference", context.getMerchantReference(),
                        "crossBorder", context.isCrossBorder()), genericLink(context, "MOBILE_MONEY_ACTIVITY").orElse(null))));
        return detail(RecordType.MULTI_ASSET_CUSTOMER, id, customer.getDisplayName(), data, links, occurrences);
    }

    private RecordDetail multiAssetTransactionDetail(Long id) {
        MultiAssetTransaction transaction = find(MultiAssetTransaction.class, id, "Multi-asset transaction");
        requireScope(transaction.getPspId());
        Map<String, Object> data = values(
                "transactionId", transaction.getId(), "externalTransactionId", transaction.getExternalTransactionId(),
                "assetClass", transaction.getAssetClass(), "transactionType", transaction.getTransactionType(),
                "amount", transaction.getAmount(), "fiatEquivalentUsd", transaction.getFiatEquivalentUsd(),
                "currency", transaction.getCurrency(), "executedAt", transaction.getExecutedAt(), "riskScore", transaction.getRiskScore(),
                "decision", transaction.getDecision(), "travelRuleStatus", transaction.getTravelRuleStatus(), "pspId", transaction.getPspId());
        List<RecordLink> links = new ArrayList<>();
        links.add(customerLink(transaction.getCustomer(), "CUSTOMER"));
        List<MultiAssetRiskSignal> signals = entityManager.createQuery(
                "select s from MultiAssetRiskSignal s where s.transaction.id = :id order by s.createdAt asc", MultiAssetRiskSignal.class)
                .setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList();
        List<RecordOccurrence> occurrences = new ArrayList<>();
        entityManager.createQuery(
                "select c from MobileMoneyTransactionContext c where c.transaction.id = :id",
                MobileMoneyTransactionContext.class).setParameter("id", id)
                .getResultStream().findFirst()
                .flatMap(context -> genericLink(context, "MOBILE_MONEY_CONTEXT"))
                .ifPresent(links::add);
        entityManager.createQuery("select s from WalletScreeningRecord s where s.transaction.id = :id order by s.screenedAt desc",
                WalletScreeningRecord.class).setParameter("id", id).setMaxResults(RELATED_LIMIT).getResultList()
                .forEach(screening -> genericLink(screening, "WALLET_SCREENING").ifPresent(links::add));
        entityManager.createQuery("select t from TravelRuleTransfer t where t.transaction.id = :id",
                TravelRuleTransfer.class).setParameter("id", id).getResultStream().findFirst()
                .flatMap(transfer -> genericLink(transfer, "TRAVEL_RULE_TRANSFER")).ifPresent(links::add);
        occurrences.add(occurrence("MULTI_ASSET_TRANSACTION", transaction.getExecutedAt(), transaction.getTransactionType().name(), data, null));
        signals.forEach(s -> occurrences.add(occurrence("RISK_SIGNAL", s.getCreatedAt(), s.getDescription(),
                values("signalCode", s.getSignalCode(), "severity", s.getSeverity(), "scoreImpact", s.getScoreImpact()), null)));
        return detail(RecordType.MULTI_ASSET_TRANSACTION, id, transaction.getExternalTransactionId(), data, links, occurrences);
    }

    /**
     * Covers every managed entity that does not have a richer AML-specific renderer above.
     * It intentionally exposes only scalar, non-sensitive fields and navigable relationships.
     */
    private RecordDetail genericDetail(String recordType, String rawId) {
        EntityType<?> entityType = entityTypeForToken(recordType)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported record type: " + recordType));
        Object identifier = convertIdentifier(entityType, rawId);
        Object entity = entityManager.find(entityType.getJavaType(), identifier);
        if (entity == null) throw new IllegalArgumentException("Record not found: " + recordType + "/" + rawId);
        requireGenericScope(recordType, genericPspId(entityType, entity, new IdentityHashMap<>(), 0));

        Map<String, Object> data = genericData(entityType, entity);
        List<RecordLink> links = new ArrayList<>(genericLinks(entityType, entity, data));
        if (entity instanceof AuditLog auditLog && auditLog.getEntityType() != null && auditLog.getEntityId() != null) {
            referenceLink(normalizeType(auditLog.getEntityType()), auditLog.getEntityId(), "AUDITED_RECORD")
                    .ifPresent(links::add);
        }
        addMobileMoneyLinks(entity, links);
        LocalDateTime occurredAt = firstTimestamp(data, "createdAt", "uploadedAt", "executedAt", "updatedAt", "filedAt");
        String title = genericTitle(recordType, identifier, data);
        String description = "" + humanize(recordType) + " record";
        return new RecordDetail(recordType, String.valueOf(identifier), title, data, List.copyOf(links),
                List.of(occurrence("RECORD_CREATED", occurredAt, description, data, null)));
    }

    private Optional<EntityType<?>> entityTypeForToken(String recordType) {
        String normalized = normalizeType(recordType);
        String alias = switch (normalized) {
            case "KYC_DOCUMENT", "DOCUMENT" -> "MERCHANT_DOCUMENT";
            case "SAR_REPORT" -> "SUSPICIOUS_ACTIVITY_REPORT";
            default -> normalized;
        };
        return entityManager.getMetamodel().getEntities().stream()
                .filter(type -> normalizeType(type.getJavaType().getSimpleName()).equals(alias))
                .findFirst();
    }

    private void addMobileMoneyLinks(Object entity, List<RecordLink> links) {
        if (entity instanceof MobileMoneyRiskProfile profile) {
            List<MobileMoneyNetworkEdge> edges = entityManager.createQuery(
                    "select e from MobileMoneyNetworkEdge e where e.pspId = :pspId and "
                            + "((e.fromEntityType = :type and e.fromEntityReference = :reference) or "
                            + "(e.toEntityType = :type and e.toEntityReference = :reference)) order by e.lastSeenAt desc",
                    MobileMoneyNetworkEdge.class)
                    .setParameter("pspId", profile.getPspId()).setParameter("type", profile.getEntityType())
                    .setParameter("reference", profile.getEntityReference()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList();
            edges.forEach(edge -> genericLink(edge, "NETWORK_RELATIONSHIP").ifPresent(links::add));
        } else if (entity instanceof MobileMoneyNetworkEdge edge) {
            addMobileMoneyProfileLink(edge.getPspId(), edge.getFromEntityType(), edge.getFromEntityReference(),
                    "FROM_ENTITY", links);
            addMobileMoneyProfileLink(edge.getPspId(), edge.getToEntityType(), edge.getToEntityReference(),
                    "TO_ENTITY", links);
        } else if (entity instanceof VaspDirectoryEntry vasp) {
            entityManager.createQuery("select s from VaspScreeningRecord s where s.vasp.id = :id order by s.screenedAt desc",
                    VaspScreeningRecord.class).setParameter("id", vasp.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(screening -> genericLink(screening, "SANCTIONS_SCREENING").ifPresent(links::add));
            entityManager.createQuery("select w from CryptoWalletProfile w where w.vasp.id = :id order by w.updatedAt desc",
                    CryptoWalletProfile.class).setParameter("id", vasp.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(wallet -> genericLink(wallet, "LINKED_WALLET").ifPresent(links::add));
            entityManager.createQuery("select t from TravelRuleTransfer t where t.originatorVasp.id = :id or t.beneficiaryVasp.id = :id order by t.createdAt desc",
                    TravelRuleTransfer.class).setParameter("id", vasp.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(transfer -> genericLink(transfer, "TRAVEL_RULE_TRANSFER").ifPresent(links::add));
        } else if (entity instanceof CryptoWalletProfile wallet) {
            entityManager.createQuery("select s from WalletScreeningRecord s where s.walletProfile.id = :id order by s.screenedAt desc",
                    WalletScreeningRecord.class).setParameter("id", wallet.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(screening -> genericLink(screening, "SCREENING_HISTORY").ifPresent(links::add));
            entityManager.createQuery("select t from TravelRuleTransfer t where t.transaction.sourceAccount.id = :accountId or t.transaction.destinationAccount.id = :accountId order by t.createdAt desc",
                    TravelRuleTransfer.class).setParameter("accountId", wallet.getAssetAccount().getId())
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(transfer -> genericLink(transfer, "TRAVEL_RULE_TRANSFER").ifPresent(links::add));
        } else if (entity instanceof VaspScreeningRecord screening) {
            entityManager.createQuery("select a from Alert a where a.pspId = :pspId and a.sourceType = :sourceType and a.sourceReference = :reference",
                    Alert.class).setParameter("pspId", screening.getPspId())
                    .setParameter("sourceType", "VASP_SANCTIONS_SCREENING")
                    .setParameter("reference", String.valueOf(screening.getVasp().getId()))
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(alert -> genericLink(alert, "GENERATED_ALERT").ifPresent(links::add));
        } else if (entity instanceof WalletScreeningRecord screening) {
            entityManager.createQuery("select a from Alert a where a.pspId = :pspId and a.sourceType = :sourceType and a.sourceReference = :reference",
                    Alert.class).setParameter("pspId", screening.getPspId())
                    .setParameter("sourceType", "CRYPTO_WALLET_SCREENING")
                    .setParameter("reference", String.valueOf(screening.getId()))
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(alert -> genericLink(alert, "GENERATED_ALERT").ifPresent(links::add));
        } else if (entity instanceof TravelRuleTransfer transfer) {
            entityManager.createQuery("select a from TravelRuleTransmissionAttempt a where a.transfer.id = :id order by a.attemptNumber asc",
                    TravelRuleTransmissionAttempt.class).setParameter("id", transfer.getId())
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(attempt -> genericLink(attempt, "TRANSMISSION_ATTEMPT").ifPresent(links::add));
            entityManager.createQuery("select s from WalletScreeningRecord s where s.transaction.id = :transactionId order by s.screenedAt desc",
                    WalletScreeningRecord.class).setParameter("transactionId", transfer.getTransaction().getId())
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(screening -> genericLink(screening, "WALLET_SCREENING").ifPresent(links::add));
        } else if (entity instanceof TravelRuleJurisdictionPolicy policy) {
            entityManager.createQuery("select t from TravelRuleTransfer t where t.policy.id = :id order by t.createdAt desc",
                    TravelRuleTransfer.class).setParameter("id", policy.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(transfer -> genericLink(transfer, "POLICY_APPLICATION").ifPresent(links::add));
        } else if (entity instanceof VirtualAssetRegulatorAccessGrant grant) {
            entityManager.createQuery("select l from VirtualAssetRegulatorAccessLog l where l.grant.id = :id order by l.accessedAt desc",
                    VirtualAssetRegulatorAccessLog.class).setParameter("id", grant.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(log -> genericLink(log, "ACCESS_LOG").ifPresent(links::add));
        } else if (entity instanceof CorporateIntelligenceCheck check) {
            entityManager.createQuery(
                    "select a from Alert a where a.pspId = :pspId and a.sourceType = :sourceType and a.sourceReference = :reference",
                    Alert.class).setParameter("pspId", check.getPspId())
                    .setParameter("sourceType", "CORPORATE_INTELLIGENCE")
                    .setParameter("reference", String.valueOf(check.getId()))
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(alert -> genericLink(alert, "GENERATED_ALERT").ifPresent(links::add));
        } else if (entity instanceof MarketOrder order) {
            entityManager.createQuery("select e from MarketExecution e where e.order.id = :id order by e.executedAt desc",
                    MarketExecution.class).setParameter("id", order.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(execution -> genericLink(execution, "EXECUTION").ifPresent(links::add));
            entityManager.createQuery("select s from MarketSurveillanceSignal s where s.order.id = :id order by s.createdAt desc",
                    MarketSurveillanceSignal.class).setParameter("id", order.getId())
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(signal -> genericLink(signal, "MARKET_ABUSE_SIGNAL").ifPresent(links::add));
            entityManager.createQuery("select f from FixMessageEvent f where f.marketOrderId = :id order by f.receivedAt desc",
                    FixMessageEvent.class).setParameter("id", order.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(event -> genericLink(event, "FIX_MESSAGE").ifPresent(links::add));
        } else if (entity instanceof MarketExecution execution) {
            genericLink(execution.getOrder(), "ORDER").ifPresent(links::add);
            entityManager.createQuery("select s from MarketSurveillanceSignal s where s.execution.id = :id order by s.createdAt desc",
                    MarketSurveillanceSignal.class).setParameter("id", execution.getId())
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(signal -> genericLink(signal, "MARKET_ABUSE_SIGNAL").ifPresent(links::add));
            entityManager.createQuery("select f from FixMessageEvent f where f.marketExecutionId = :id order by f.receivedAt desc",
                    FixMessageEvent.class).setParameter("id", execution.getId()).setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList().forEach(event -> genericLink(event, "FIX_MESSAGE").ifPresent(links::add));
        } else if (entity instanceof MarketSurveillanceSignal signal) {
            entityManager.createQuery(
                    "select a from Alert a where a.pspId = :pspId and a.sourceType = :sourceType and a.sourceReference = :reference",
                    Alert.class).setParameter("pspId", signal.getPspId())
                    .setParameter("sourceType", "MARKET_SURVEILLANCE")
                    .setParameter("reference", String.valueOf(signal.getId()))
                    .setMaxResults(GENERIC_RELATED_LIMIT).getResultList()
                    .forEach(alert -> genericLink(alert, "GENERATED_ALERT").ifPresent(links::add));
        } else if (entity instanceof RegulatorySubmission submission) {
            if (submission.getExecutionId() != null) {
                referenceLink("REPORT_EXECUTION", String.valueOf(submission.getExecutionId()), "SOURCE_EXECUTION")
                        .ifPresent(links::add);
            }
            entityManager.createQuery(
                    "select a from RegulatorySubmissionAttempt a where a.submissionId = :id order by a.attemptNo desc",
                    RegulatorySubmissionAttempt.class)
                    .setParameter("id", submission.getId())
                    .setMaxResults(GENERIC_RELATED_LIMIT)
                    .getResultList()
                    .forEach(attempt -> genericLink(attempt, "TRANSMISSION_ATTEMPT").ifPresent(links::add));
        } else if (entity instanceof RegulatorySubmissionAttempt attempt) {
            referenceLink("REGULATORY_SUBMISSION", String.valueOf(attempt.getSubmissionId()), "SUBMISSION")
                    .ifPresent(links::add);
        } else if (entity instanceof CbkSubmission submission) {
            referenceLink("PSP", String.valueOf(submission.getPspId()), "REPORTING_PSP")
                    .ifPresent(links::add);
        }
    }

    private void addMobileMoneyProfileLink(Long pspId,
            com.posgateway.aml.entity.mobilemoney.MobileMoneyEntityType entityType,
            String entityReference, String relationship, List<RecordLink> links) {
        entityManager.createQuery(
                "select p from MobileMoneyRiskProfile p where p.pspId = :pspId and p.entityType = :type and p.entityReference = :reference",
                MobileMoneyRiskProfile.class)
                .setParameter("pspId", pspId).setParameter("type", entityType)
                .setParameter("reference", entityReference).getResultStream().findFirst()
                .flatMap(profile -> genericLink(profile, relationship)).ifPresent(links::add);
    }

    private Optional<EntityType<?>> entityTypeForEntity(Object entity) {
        if (entity == null) return Optional.empty();
        Class<?> entityClass = entity.getClass();
        return entityManager.getMetamodel().getEntities().stream()
                .filter(type -> type.getJavaType().isAssignableFrom(entityClass)
                        || entityClass.isAssignableFrom(type.getJavaType()))
                .findFirst();
    }

    private Object convertIdentifier(EntityType<?> entityType, String rawId) {
        Class<?> idType = entityType.getIdType().getJavaType();
        try {
            if (idType == String.class) return rawId;
            if (idType == Long.class || idType == long.class) return Long.valueOf(rawId);
            if (idType == Integer.class || idType == int.class) return Integer.valueOf(rawId);
            if (idType == UUID.class) return UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid identifier for " + humanize(normalizeType(entityType.getJavaType().getSimpleName())));
        }
        throw new IllegalArgumentException("Unsupported identifier type for " + entityType.getJavaType().getSimpleName());
    }

    private Map<String, Object> genericData(EntityType<?> entityType, Object entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (attribute.isAssociation() || attribute.isCollection() || isSensitive(attribute.getName())) continue;
            Object rawValue = readField(entity, attribute.getName());
            Object value = safeValue(rawValue);
            if (value != null) data.put(attribute.getName(), value);
        }
        return data;
    }

    private List<RecordLink> genericLinks(EntityType<?> entityType, Object entity, Map<String, Object> data) {
        List<RecordLink> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (!attribute.isAssociation()) continue;
            Object value = readField(entity, attribute.getName());
            if (value instanceof Collection<?> collection) {
                collection.stream().limit(GENERIC_RELATED_LIMIT)
                        .map(item -> genericLink(item, attribute.getName()))
                        .flatMap(Optional::stream)
                        .filter(link -> seen.add(link.recordType() + ":" + link.recordId()))
                        .forEach(links::add);
            } else {
                genericLink(value, attribute.getName()).filter(link -> seen.add(link.recordType() + ":" + link.recordId()))
                        .ifPresent(links::add);
            }
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String targetType = FOREIGN_KEY_TYPES.get(normalizeField(entry.getKey()));
            if (targetType == null || entry.getValue() == null) continue;
            referenceLink(targetType, entry.getValue(), entry.getKey()).filter(link -> seen.add(link.recordType() + ":" + link.recordId()))
                    .ifPresent(links::add);
        }
        return links;
    }

    private Optional<RecordLink> genericLink(Object relatedEntity, String relationship) {
        Optional<EntityType<?>> type = entityTypeForEntity(relatedEntity);
        if (type.isEmpty()) return Optional.empty();
        Object identifier = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(relatedEntity);
        if (identifier == null || !isInScope(type.get(), relatedEntity)) return Optional.empty();
        String token = normalizeType(type.get().getJavaType().getSimpleName());
        Map<String, Object> summary = genericData(type.get(), relatedEntity);
        return Optional.of(link(token, String.valueOf(identifier), genericTitle(token, identifier, summary), relationship, summary));
    }

    private Optional<RecordLink> referenceLink(String targetType, Object rawId, String relationship) {
        try {
            if (RecordType.REPORT_EXECUTION.name().equals(targetType)) {
                ReportExecution execution = reportExecution(String.valueOf(rawId));
                if (!hasScope(execution.getPspId())) return Optional.empty();
                return Optional.of(link(targetType, execution.getExecutionId(), "Report " + execution.getExecutionId(), relationship,
                        values("status", execution.getStatus(), "totalRecords", execution.getTotalRecords())));
            }
            if (RecordType.MERCHANT.name().equals(targetType)) {
                Merchant merchant = entityManager.find(Merchant.class, Long.valueOf(String.valueOf(rawId)));
                return merchant == null || !hasScope(merchant.getPsp() == null ? null : merchant.getPsp().getPspId())
                        ? Optional.empty() : Optional.of(merchantLink(merchant, relationship));
            }
            if (RecordType.ALERT.name().equals(targetType)) {
                Alert alert = entityManager.find(Alert.class, Long.valueOf(String.valueOf(rawId)));
                return alert == null || !hasScope(alert.getPspId() != null ? alert.getPspId() : merchantPspId(alert.getMerchantId()))
                        ? Optional.empty() : Optional.of(alertLink(alert, relationship));
            }
            if (RecordType.CASE.name().equals(targetType)) {
                ComplianceCase complianceCase = entityManager.find(ComplianceCase.class, Long.valueOf(String.valueOf(rawId)));
                return complianceCase == null || !hasScope(complianceCase.getPspId())
                        ? Optional.empty() : Optional.of(caseLink(complianceCase, relationship));
            }
            if (RecordType.TRANSACTION.name().equals(targetType)) {
                TransactionEntity transaction = entityManager.find(TransactionEntity.class, Long.valueOf(String.valueOf(rawId)));
                return transaction == null || !hasScope(transaction.getPspId())
                        ? Optional.empty() : Optional.of(transactionLink(transaction, relationship));
            }
            EntityType<?> entityType = entityTypeForToken(targetType).orElse(null);
            if (entityType == null) return Optional.empty();
            Object entity = entityManager.find(entityType.getJavaType(), convertIdentifier(entityType, String.valueOf(rawId)));
            return genericLink(entity, relationship);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private boolean isInScope(EntityType<?> entityType, Object entity) {
        String token = normalizeType(entityType.getJavaType().getSimpleName());
        Long recordPspId = genericPspId(entityType, entity, new IdentityHashMap<>(), 0);
        return hasScope(recordPspId) || (recordPspId == null && isSharedReference(token));
    }

    private Long genericPspId(EntityType<?> entityType, Object entity, IdentityHashMap<Object, Boolean> visited, int depth) {
        if (entity == null || depth > 3 || visited.put(entity, Boolean.TRUE) != null) return null;
        Object directPspId = readField(entity, "pspId");
        if (directPspId instanceof Number number) return number.longValue();
        if (normalizeType(entityType.getJavaType().getSimpleName()).equals("PSP")) {
            Object pspId = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
            return pspId instanceof Number number ? number.longValue() : null;
        }
        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            if (!attribute.isAssociation() || attribute.isCollection()) continue;
            Object related = readField(entity, attribute.getName());
            Optional<EntityType<?>> relatedType = entityTypeForEntity(related);
            if (relatedType.isEmpty()) continue;
            Long pspId = genericPspId(relatedType.get(), related, visited, depth + 1);
            if (pspId != null) return pspId;
        }
        for (Attribute<?, ?> attribute : entityType.getAttributes()) {
            String targetType = FOREIGN_KEY_TYPES.get(normalizeField(attribute.getName()));
            if (targetType == null) continue;
            Object foreignKey = readField(entity, attribute.getName());
            Long pspId = referencePspId(targetType, foreignKey);
            if (pspId != null) return pspId;
        }
        return null;
    }

    private Long referencePspId(String targetType, Object rawId) {
        if (rawId == null) return null;
        try {
            if (RecordType.MERCHANT.name().equals(targetType)) return merchantPspId(Long.valueOf(String.valueOf(rawId)));
            if (RecordType.ALERT.name().equals(targetType)) {
                Alert alert = entityManager.find(Alert.class, Long.valueOf(String.valueOf(rawId)));
                return alert == null ? null : (alert.getPspId() != null ? alert.getPspId() : merchantPspId(alert.getMerchantId()));
            }
            if (RecordType.CASE.name().equals(targetType)) {
                ComplianceCase complianceCase = entityManager.find(ComplianceCase.class, Long.valueOf(String.valueOf(rawId)));
                return complianceCase == null ? null : complianceCase.getPspId();
            }
            if (RecordType.TRANSACTION.name().equals(targetType)) {
                TransactionEntity transaction = entityManager.find(TransactionEntity.class, Long.valueOf(String.valueOf(rawId)));
                return transaction == null ? null : transaction.getPspId();
            }
            if (RecordType.REPORT_EXECUTION.name().equals(targetType)) return reportExecution(String.valueOf(rawId)).getPspId();
        } catch (RuntimeException ignored) { }
        return null;
    }

    private boolean hasScope(Long recordPspId) {
        Long currentPspId = pspIsolationService.getCurrentUserPspId();
        return currentPspId != null && (currentPspId == 0L || Objects.equals(currentPspId, recordPspId));
    }

    private void requireGenericScope(String recordType, Long recordPspId) {
        if (hasScope(recordPspId) || (recordPspId == null && isSharedReference(recordType))) return;
        throw new AccessDeniedException("Record is outside the current PSP scope");
    }

    private boolean isSharedReference(String recordType) {
        return pspIsolationService.getCurrentUserPspId() != null && SHARED_REFERENCE_TYPES.contains(recordType);
    }

    private Object readField(Object source, String name) {
        for (Class<?> type = source == null ? null : source.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException ignored) {
                // Continue into mapped superclasses.
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object safeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof TemporalAccessor) return value;
        if (value instanceof UUID) return value.toString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safeMap = new LinkedHashMap<>();
            map.forEach((key, item) -> { if (!isSensitive(String.valueOf(key))) safeMap.put(String.valueOf(key), safeValue(item)); });
            return safeMap;
        }
        return String.valueOf(value);
    }

    private boolean isSensitive(String field) {
        String normalized = normalizeField(field);
        if (SAFE_INTEGRITY_HASH_FIELDS.contains(normalized)) return false;
        return SENSITIVE_FIELD_PARTS.stream().anyMatch(normalized::contains);
    }

    private String normalizeField(String value) { return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT); }
    private String genericTitle(String token, Object identifier, Map<String, Object> data) {
        for (String field : List.of("displayName", "legalName", "businessName", "name", "title", "submissionReference", "caseReference", "fileName", "email", "code", "externalId")) {
            Object value = data.get(field);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return humanize(token) + " #" + identifier;
    }
    private String humanize(String token) {
        String lower = token.replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(lower.length());
        boolean capitalize = true;
        for (char character : lower.toCharArray()) {
            result.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return result.toString();
    }
    private LocalDateTime firstTimestamp(Map<String, Object> data, String... names) {
        for (String name : names) if (data.get(name) instanceof LocalDateTime timestamp) return timestamp;
        return null;
    }

    private RecordDetail reportExecutionDetail(String id) {
        ReportExecution execution = reportExecution(id);
        requireScope(execution.getPspId());
        Map<String, Object> data = values(
                "executionId", execution.getExecutionId(), "reportName", execution.getReport() == null ? null : execution.getReport().getReportName(),
                "status", execution.getStatus(), "totalRecords", execution.getTotalRecords(), "parameters", execution.getParameters(),
                "sourceContext", execution.getSourceContext(), "calculationSummary", execution.getCalculationSummary(),
                "startedAt", execution.getStartedAt(), "completedAt", execution.getCompletedAt(), "pspId", execution.getPspId());
        List<RecordLink> links = new ArrayList<>();
        if (execution.getRecordLinks() != null) {
            execution.getRecordLinks().forEach(item -> {
                Object type = item.get("recordType"); Object recordId = item.get("recordId");
                if (type != null && recordId != null) resolveLink(String.valueOf(type), recordId).ifPresent(links::add);
            });
        }
        entityManager.createQuery(
                "select s from RegulatorySubmission s where s.executionId = :executionId order by s.createdAt desc",
                RegulatorySubmission.class)
                .setParameter("executionId", execution.getId())
                .setMaxResults(GENERIC_RELATED_LIMIT)
                .getResultList()
                .forEach(submission -> genericLink(submission, "REGULATORY_FILING")
                        .ifPresent(links::add));
        return detail(RecordType.REPORT_EXECUTION, execution.getExecutionId(), "Report " + execution.getExecutionId(), data, links,
                List.of(occurrence("REPORT_EXECUTED", execution.getCompletedAt() != null ? execution.getCompletedAt() : execution.getStartedAt(),
                        "Report execution", data, null)));
    }

    private ReportExecution reportExecution(String id) {
        try {
            Long numericId = Long.valueOf(id);
            ReportExecution byId = entityManager.find(ReportExecution.class, numericId);
            if (byId != null) return byId;
        } catch (NumberFormatException ignored) { }
        return entityManager.createQuery("select r from ReportExecution r where r.executionId = :id", ReportExecution.class)
                .setParameter("id", id).getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Report execution not found: " + id));
    }

    private Optional<Merchant> merchant(Long id) { return Optional.ofNullable(entityManager.find(Merchant.class, id)); }
    private Optional<TransactionEntity> transaction(Long id) { return Optional.ofNullable(entityManager.find(TransactionEntity.class, id)); }
    private Optional<MultiAssetCustomer> customer(Long id) { return Optional.ofNullable(entityManager.find(MultiAssetCustomer.class, id)); }
    private Optional<Merchant> merchantFromTransaction(TransactionEntity transaction) {
        try { return transaction.getMerchantId() == null ? Optional.empty() : merchant(Long.valueOf(transaction.getMerchantId())); }
        catch (NumberFormatException ignored) { return Optional.empty(); }
    }
    private Long merchantPspId(Long merchantId) { return merchantId == null ? null : merchant(merchantId).map(m -> m.getPsp() == null ? null : m.getPsp().getPspId()).orElse(null); }

    private <T> T find(Class<T> type, Long id, String label) {
        T entity = entityManager.find(type, id);
        if (entity == null) throw new IllegalArgumentException(label + " not found: " + id);
        return entity;
    }
    private String normalizeType(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Record type is required");
        return value.trim().replace('-', '_').replace(' ', '_').replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_")
                .toUpperCase(Locale.ROOT);
    }
    private Long parseId(String value) {
        try { return Long.valueOf(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Record ID must be numeric: " + value); }
    }
    private void requireScope(Long recordPspId) {
        Long currentPspId = pspIsolationService.getCurrentUserPspId();
        if (currentPspId == null || (currentPspId != 0L && !Objects.equals(currentPspId, recordPspId))) {
            throw new AccessDeniedException("Record is outside the current PSP scope");
        }
    }
    private RecordDetail detail(RecordType type, Object id, String title, Map<String, Object> data,
            List<RecordLink> links, List<RecordOccurrence> occurrences) {
        return new RecordDetail(type.name(), String.valueOf(id), title, data, List.copyOf(links), List.copyOf(occurrences));
    }
    private RecordLink alertLink(Alert alert, String relation) { return link(RecordType.ALERT.name(), String.valueOf(alert.getAlertId()), "Alert #" + alert.getAlertId(), relation, values("severity", alert.getSeverity(), "status", alert.getStatus(), "score", alert.getScore())); }
    private RecordLink caseLink(ComplianceCase c, String relation) { return link(RecordType.CASE.name(), String.valueOf(c.getId()), c.getCaseReference(), relation, values("status", c.getStatus(), "priority", c.getPriority())); }
    private RecordLink transactionLink(TransactionEntity t, String relation) { return link(RecordType.TRANSACTION.name(), String.valueOf(t.getTxnId()), "Transaction #" + t.getTxnId(), relation, values("amountCents", t.getAmountCents(), "currency", t.getCurrency(), "decision", t.getDecision())); }
    private RecordLink merchantLink(Merchant m, String relation) { return link(RecordType.MERCHANT.name(), String.valueOf(m.getMerchantId()), m.getLegalName(), relation, values("status", m.getStatus(), "riskLevel", m.getRiskLevel())); }
    private RecordLink customerLink(MultiAssetCustomer c, String relation) { return link(RecordType.MULTI_ASSET_CUSTOMER.name(), String.valueOf(c.getId()), c.getDisplayName(), relation, values("externalCustomerId", c.getExternalCustomerId(), "riskTier", c.getRiskTier())); }
    private RecordLink multiAssetTransactionLink(MultiAssetTransaction t, String relation) { return link(RecordType.MULTI_ASSET_TRANSACTION.name(), String.valueOf(t.getId()), t.getExternalTransactionId(), relation, values("assetClass", t.getAssetClass(), "riskScore", t.getRiskScore(), "decision", t.getDecision())); }
    private RecordLink link(String type, String id, String label, String relation, Map<String, Object> summary) { return new RecordLink(type, id, label, relation, summary); }
    private RecordOccurrence occurrence(String type, LocalDateTime at, String description, Map<String, Object> data, RecordLink source) { return new RecordOccurrence(type, at, description, data, source); }
    private Map<String, Object> values(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) if (values[i + 1] != null) map.put(String.valueOf(values[i]), values[i + 1]);
        return map;
    }
}
