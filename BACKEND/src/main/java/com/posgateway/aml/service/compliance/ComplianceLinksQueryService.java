package com.posgateway.aml.service.compliance;

import com.posgateway.aml.dto.compliance.ComplianceLinksResponse;
import com.posgateway.aml.dto.compliance.ComplianceLinksResponse.LinkedRecord;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.repository.compliance.AlertTransactionLinkRepository;
import com.posgateway.aml.repository.compliance.SarAlertLinkRepository;
import com.posgateway.aml.repository.merchant.MerchantRiskScoreRepository;
import com.posgateway.aml.repository.monitoring.MonitoringAlertRepository;
import com.posgateway.aml.repository.multiasset.AssetAccountRepository;
import com.posgateway.aml.repository.sanctions.ScreeningActivityRepository;
import com.posgateway.aml.entity.multiasset.AssetAccount;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-side aggregator for indexed FK bridges between fraud alerts, AML artifacts, merchants, cards, and txns.
 */
@Service
@Transactional(readOnly = true)
public class ComplianceLinksQueryService {

    private final AlertRepository alertRepository;
    private final AlertTransactionLinkRepository alertTransactionLinkRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final SarAlertLinkRepository sarAlertLinkRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final ChargebackDisputeRepository disputeRepository;
    private final MonitoringAlertRepository monitoringAlertRepository;
    private final MerchantRiskScoreRepository merchantRiskScoreRepository;
    private final ScreeningActivityRepository screeningActivityRepository;
    private final AssetAccountRepository assetAccountRepository;

    public ComplianceLinksQueryService(AlertRepository alertRepository,
                                       AlertTransactionLinkRepository alertTransactionLinkRepository,
                                       ComplianceCaseRepository complianceCaseRepository,
                                       SarAlertLinkRepository sarAlertLinkRepository,
                                       SuspiciousActivityReportRepository sarRepository,
                                       ChargebackDisputeRepository disputeRepository,
                                       MonitoringAlertRepository monitoringAlertRepository,
                                       MerchantRiskScoreRepository merchantRiskScoreRepository,
                                       ScreeningActivityRepository screeningActivityRepository,
                                       AssetAccountRepository assetAccountRepository) {
        this.alertRepository = alertRepository;
        this.alertTransactionLinkRepository = alertTransactionLinkRepository;
        this.complianceCaseRepository = complianceCaseRepository;
        this.sarAlertLinkRepository = sarAlertLinkRepository;
        this.sarRepository = sarRepository;
        this.disputeRepository = disputeRepository;
        this.monitoringAlertRepository = monitoringAlertRepository;
        this.merchantRiskScoreRepository = merchantRiskScoreRepository;
        this.screeningActivityRepository = screeningActivityRepository;
        this.assetAccountRepository = assetAccountRepository;
    }

    /** Returns the compliance graph anchored on a transaction id. */
    public ComplianceLinksResponse forTransaction(Long txnId) {
        List<LinkedRecord> alerts = new ArrayList<>();
        alertRepository.findByTxnId(txnId).forEach(a ->
                alerts.add(link("ALERT", a.getAlertId(), "FRAUD_ALERT", a.getReason())));
        alertTransactionLinkRepository.findByTxnIdOrderByIdDesc(txnId).forEach(row ->
                alertRepository.findById(row.getAlertId()).ifPresent(a ->
                        alerts.add(link("ALERT", a.getAlertId(), "LINKED_ALERT", a.getReason()))));
        List<LinkedRecord> screening = new ArrayList<>();
        screeningActivityRepository.findByTxnIdOrderByCreatedAtDesc(txnId, PageRequest.of(0, 20))
                .forEach(sa -> screening.add(link("SCREENING_ACTIVITY", sa.getId(), "SCREENING", sa.getReason())));
        return graphFor(String.valueOf(txnId), link("TRANSACTION", txnId, "SELF", "Transaction #" + txnId),
                alerts, screening);
    }

    /**
     * Returns the compliance graph anchored on a decision record (R6, by ID reference only): alerts
     * that name it directly or through {@code alert_transactions}, their cases, SARs and disputes, and
     * its screening runs. {@code pspId} scopes every row to one PSP; {@code null} (platform
     * administrators) reads across PSPs, as the cloud workflow views already allow them to.
     */
    public ComplianceLinksResponse forDecisionRecord(Long pspId, java.util.UUID edgeAssessmentId) {
        List<LinkedRecord> alerts = new ArrayList<>();
        (pspId == null ? alertRepository.findByEdgeAssessmentIdOrderByCreatedAtDesc(edgeAssessmentId)
                : alertRepository.findByPspIdAndEdgeAssessmentIdOrderByCreatedAtDesc(pspId, edgeAssessmentId))
                .forEach(a -> alerts.add(link("ALERT", a.getAlertId(), "FRAUD_ALERT", a.getReason())));
        alertTransactionLinkRepository.findByEdgeAssessmentIdOrderByIdDesc(edgeAssessmentId).forEach(row ->
                alertRepository.findById(row.getAlertId())
                        .filter(a -> pspId == null || pspId.equals(a.getPspId()))
                        .ifPresent(a -> alerts.add(link("ALERT", a.getAlertId(), "LINKED_ALERT", a.getReason()))));
        List<LinkedRecord> screening = new ArrayList<>();
        screeningActivityRepository.findByEdgeAssessmentIdOrderByCreatedAtDesc(edgeAssessmentId, PageRequest.of(0, 20))
                .stream().filter(sa -> pspId == null || pspId.equals(sa.getPspId()))
                .forEach(sa -> screening.add(link("SCREENING_ACTIVITY", sa.getId(), "SCREENING", sa.getReason())));
        String id = edgeAssessmentId.toString();
        return graphFor(id, link("TRANSACTION", id, "SELF", "Transaction " + id), alerts, screening);
    }

    /** Cases, SARs and disputes reached from a transaction's alerts, plus the anchor and screening. */
    private ComplianceLinksResponse graphFor(String anchorId, LinkedRecord self, List<LinkedRecord> alerts,
                                             List<LinkedRecord> screening) {
        List<LinkedRecord> cases = new ArrayList<>();
        dedupe(alerts).forEach(a -> alertRepository.findById(Long.valueOf(a.recordId())).ifPresent(alert -> {
            if (alert.getComplianceCaseId() != null) {
                complianceCaseRepository.findById(alert.getComplianceCaseId()).ifPresent(c ->
                        cases.add(link("CASE", c.getId(), "ESCALATED_CASE", c.getCaseReference())));
            }
        }));

        List<LinkedRecord> sars = new ArrayList<>();
        dedupe(alerts).forEach(a -> sarAlertLinkRepository.findByAlertId(Long.valueOf(a.recordId())).forEach(row ->
                sars.add(link("SAR", row.getSarId(), "FILED_SAR", "SAR #" + row.getSarId()))));

        List<LinkedRecord> disputes = new ArrayList<>();
        dedupe(alerts).forEach(a -> {
            Long alertId = Long.valueOf(a.recordId());
            alertRepository.findById(alertId).ifPresent(alert -> {
                if (alert.getMerchantId() == null) {
                    return;
                }
                disputeRepository.findByMerchantIdOrderByCreatedAtDesc(alert.getMerchantId()).stream()
                        .filter(d -> alertId.equals(d.getAlertId()))
                        .forEach(d -> disputes.add(link("CHARGEBACK_DISPUTE", d.getId(), "DISPUTE", d.getNotificationType())));
            });
        });

        return new ComplianceLinksResponse("TRANSACTION", anchorId, dedupe(alerts), dedupe(cases),
                List.of(self), dedupe(sars), dedupe(disputes), List.of(), List.of(), dedupe(screening));
    }

    /** Returns the compliance graph anchored on a fraud alert id. */
    public ComplianceLinksResponse forAlert(Long alertId) {
        List<LinkedRecord> alerts = new ArrayList<>();
        alertRepository.findById(alertId).ifPresent(a ->
                alerts.add(link("ALERT", a.getAlertId(), "SELF", "Alert #" + a.getAlertId())));

        List<LinkedRecord> transactions = new ArrayList<>();
        alertRepository.findById(alertId).ifPresent(a -> {
            if (a.getTxnId() != null) {
                transactions.add(link("TRANSACTION", a.getTxnId(), "TRIGGERED_BY_TRANSACTION", "Txn #" + a.getTxnId()));
            }
        });

        List<LinkedRecord> cases = new ArrayList<>();
        alertRepository.findById(alertId).ifPresent(a -> {
            if (a.getComplianceCaseId() != null) {
                complianceCaseRepository.findById(a.getComplianceCaseId()).ifPresent(c ->
                        cases.add(link("CASE", c.getId(), "ESCALATED_CASE", c.getCaseReference())));
            }
        });

        List<LinkedRecord> sars = new ArrayList<>();
        sarAlertLinkRepository.findByAlertId(alertId).forEach(row ->
                sars.add(link("SAR", row.getSarId(), "FILED_SAR", "SAR #" + row.getSarId())));

        return new ComplianceLinksResponse("ALERT", String.valueOf(alertId), dedupe(alerts), dedupe(cases),
                dedupe(transactions), dedupe(sars), List.of(), List.of(), List.of(), List.of());
    }

    /** Returns the compliance graph anchored on a compliance case id. */
    public ComplianceLinksResponse forCase(Long caseId) {
        List<LinkedRecord> cases = new ArrayList<>();
        complianceCaseRepository.findById(caseId).ifPresent(c ->
                cases.add(link("CASE", c.getId(), "SELF", c.getCaseReference())));

        List<LinkedRecord> alerts = new ArrayList<>();
        alertRepository.findByComplianceCaseId(caseId).forEach(a ->
                alerts.add(link("ALERT", a.getAlertId(), "CASE_ALERT", a.getReason())));

        List<LinkedRecord> sars = new ArrayList<>();
        sarRepository.findByComplianceCase_Id(caseId).forEach(s ->
                sars.add(link("SAR", s.getId(), "CASE_SAR", s.getSarReference())));

        List<LinkedRecord> subjectLinks = new ArrayList<>();
        complianceCaseRepository.findById(caseId).ifPresent(c -> {
            if (c.getMerchantId() != null) {
                subjectLinks.add(link("MERCHANT", c.getMerchantId(), "SUBJECT", "Merchant " + c.getMerchantId()));
            }
        });

        return new ComplianceLinksResponse("CASE", String.valueOf(caseId), dedupe(alerts), dedupe(cases),
                dedupe(subjectLinks), dedupe(sars), List.of(), List.of(), List.of(), List.of());
    }

    /** Returns the compliance graph anchored on a SAR id. */
    public ComplianceLinksResponse forSar(Long sarId) {
        List<LinkedRecord> sars = new ArrayList<>();
        sarRepository.findById(sarId).ifPresent(s ->
                sars.add(link("SAR", s.getId(), "SELF", s.getSarReference())));

        List<LinkedRecord> alerts = new ArrayList<>();
        sarAlertLinkRepository.findBySarId(sarId).forEach(row ->
                alertRepository.findById(row.getAlertId()).ifPresent(a ->
                        alerts.add(link("ALERT", a.getAlertId(), "LINKED_ALERT", a.getReason()))));

        List<LinkedRecord> cases = new ArrayList<>();
        sarRepository.findById(sarId).ifPresent(s -> {
            if (s.getComplianceCase() != null) {
                cases.add(link("CASE", s.getComplianceCase().getId(), "SOURCE_CASE",
                        s.getComplianceCase().getCaseReference()));
            }
        });

        return new ComplianceLinksResponse("SAR", String.valueOf(sarId), dedupe(alerts), dedupe(cases),
                List.of(), dedupe(sars), List.of(), List.of(), List.of(), List.of());
    }

    /** Returns the compliance graph anchored on a multi-asset customer id. */
    public ComplianceLinksResponse forCustomer(Long customerId) {
        List<LinkedRecord> alerts = new ArrayList<>();
        alertRepository.findByMultiAssetCustomerIdOrderByCreatedAtDesc(customerId).stream().limit(100).forEach(a ->
                alerts.add(link("ALERT", a.getAlertId(), "CUSTOMER_ALERT", a.getReason())));

        List<LinkedRecord> txns = new ArrayList<>();
        alertRepository.findByMultiAssetCustomerIdOrderByCreatedAtDesc(customerId).stream().limit(50)
                .filter(a -> a.getTxnId() != null)
                .forEach(a -> txns.add(link("TRANSACTION", a.getTxnId(), "CUSTOMER_TXN", "Txn #" + a.getTxnId())));

        return new ComplianceLinksResponse("MULTI_ASSET_CUSTOMER", String.valueOf(customerId),
                dedupe(alerts), List.of(), dedupe(txns), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Returns the compliance graph anchored on a chargeback dispute id. */
    public ComplianceLinksResponse forDispute(Long disputeId) {
        List<LinkedRecord> disputes = new ArrayList<>();
        disputeRepository.findById(disputeId).ifPresent(d ->
                disputes.add(link("CHARGEBACK_DISPUTE", d.getId(), "SELF", d.getNotificationType())));

        List<LinkedRecord> alerts = new ArrayList<>();
        List<LinkedRecord> cases = new ArrayList<>();
        disputeRepository.findById(disputeId).ifPresent(d -> {
            if (d.getAlertId() != null) {
                alertRepository.findById(d.getAlertId()).ifPresent(a ->
                        alerts.add(link("ALERT", a.getAlertId(), "DISPUTE_ALERT", a.getReason())));
            }
            if (d.getComplianceCaseId() != null) {
                complianceCaseRepository.findById(d.getComplianceCaseId()).ifPresent(c ->
                        cases.add(link("CASE", c.getId(), "DISPUTE_CASE", c.getCaseReference())));
            }
        });
        List<LinkedRecord> merchants = new ArrayList<>();
        disputeRepository.findById(disputeId).ifPresent(d -> {
            if (d.getMerchantId() != null) {
                merchants.add(link("MERCHANT", d.getMerchantId(), "DISPUTE_MERCHANT", "Merchant " + d.getMerchantId()));
            }
        });

        return new ComplianceLinksResponse("CHARGEBACK_DISPUTE", String.valueOf(disputeId),
                dedupe(alerts), dedupe(cases), dedupe(merchants), List.of(), dedupe(disputes), List.of(), List.of(), List.of());
    }

    /** Returns the compliance graph anchored on a card or wallet asset account id. */
    public ComplianceLinksResponse forAssetAccount(Long accountId) {
        return assetAccountRepository.findById(accountId)
                .map(this::linksForAssetAccount)
                .orElseGet(() -> new ComplianceLinksResponse("ASSET_ACCOUNT", String.valueOf(accountId),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    /** Builds correlated links for an asset account via its owning multi-asset customer. */
    private ComplianceLinksResponse linksForAssetAccount(AssetAccount account) {
        Long customerId = account.getCustomer().getId();
        ComplianceLinksResponse customerLinks = forCustomer(customerId);
        List<LinkedRecord> related = new ArrayList<>();
        related.add(link("MULTI_ASSET_CUSTOMER", customerId, "ACCOUNT_OWNER",
                account.getCustomer().getDisplayName()));
        related.addAll(customerLinks.transactions());
        return new ComplianceLinksResponse("ASSET_ACCOUNT", String.valueOf(account.getId()),
                customerLinks.alerts(), customerLinks.cases(), dedupe(related), customerLinks.sars(),
                customerLinks.disputes(), customerLinks.monitoringAlerts(), customerLinks.riskProfiles(),
                customerLinks.screeningActivities());
    }

    /** Returns the compliance graph anchored on a merchant id. */
    public ComplianceLinksResponse forMerchant(Long merchantId) {
        List<LinkedRecord> alerts = new ArrayList<>();
        alertRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream().limit(200).forEach(a ->
                alerts.add(link("ALERT", a.getAlertId(), "MERCHANT_ALERT", a.getReason())));

        List<LinkedRecord> cases = new ArrayList<>();
        complianceCaseRepository.findByMerchantId(merchantId).forEach(c ->
                cases.add(link("CASE", c.getId(), "CASE", c.getCaseReference())));

        List<LinkedRecord> risk = new ArrayList<>();
        merchantRiskScoreRepository.findLatestByMerchantId(merchantId)
                .ifPresent(score -> risk.add(link("MERCHANT_RISK_SCORE", score.getScoreId(), "RISK_PROFILE", score.getRiskLevel())));

        List<LinkedRecord> monitoring = new ArrayList<>();
        monitoringAlertRepository.findByMerchant_MerchantIdOrderByCreatedAtDesc(merchantId).forEach(m ->
                monitoring.add(link("MONITORING_ALERT", m.getAlertId(), "AML_MONITORING", m.getAlertType())));

        List<LinkedRecord> screening = new ArrayList<>();
        screeningActivityRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, PageRequest.of(0, 50))
                .forEach(sa -> screening.add(link("SCREENING_ACTIVITY", sa.getId(), "SCREENING", sa.getReason())));

        List<LinkedRecord> sars = new ArrayList<>();
        sarRepository.findByComplianceCaseMerchantId(merchantId).stream().limit(50).forEach(s ->
                sars.add(link("SAR", s.getId(), "SAR", s.getSarReference())));

        List<LinkedRecord> disputes = new ArrayList<>();
        disputeRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).forEach(d ->
                disputes.add(link("CHARGEBACK_DISPUTE", d.getId(), "DISPUTE", d.getNotificationType())));

        return new ComplianceLinksResponse("MERCHANT", String.valueOf(merchantId), dedupe(alerts), dedupe(cases),
                List.of(), dedupe(sars), dedupe(disputes), dedupe(monitoring), dedupe(risk), dedupe(screening));
    }

    private static LinkedRecord link(String type, Object id, String relationship, String label) {
        return new LinkedRecord(type, String.valueOf(id), relationship, label);
    }

    private static List<LinkedRecord> dedupe(List<LinkedRecord> input) {
        Set<String> seen = new LinkedHashSet<>();
        List<LinkedRecord> out = new ArrayList<>();
        for (LinkedRecord record : input) {
            String key = record.recordType() + ":" + record.recordId();
            if (seen.add(key)) {
                out.add(record);
            }
        }
        return out;
    }
}
