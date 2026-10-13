package com.posgateway.aml.service.export;

import com.posgateway.aml.dto.compliance.ComplianceLinksResponse;
import com.posgateway.aml.dto.export.ConsoleListViewType;
import com.posgateway.aml.entity.Alert;
import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.TransactionEntity;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.chargeback.ChargebackDispute;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.entity.admin.RuntimeError;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.messaging.Message;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.entity.multiasset.AssetAccount;
import com.posgateway.aml.entity.multiasset.MultiAssetCustomer;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.reporting.RegulatorySubmission;
import com.posgateway.aml.entity.reporting.SubmissionStatus;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.entity.sanctions.ScreeningActivity;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.repository.AlertRepository;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.chargeback.ChargebackDisputeRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.admin.RuntimeErrorRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.messaging.MessageRepository;
import com.posgateway.aml.repository.multiasset.AssetAccountRepository;
import com.posgateway.aml.repository.multiasset.MultiAssetCustomerRepository;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.repository.sanctions.ScreeningActivityRepository;
import com.posgateway.aml.service.compliance.ComplianceLinksQueryService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Counts and pages Console list rows for each supported view, applying PSP tenant isolation
 * and the same filter keys the FRONTEND encodes in URL query parameters.
 */
@Service
@Transactional(readOnly = true)
public class ConsoleListExportQueryFacade {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PspIsolationService pspIsolationService;
    private final AlertRepository alertRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final AuditLogRepository auditLogRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final ChargebackDisputeRepository chargebackDisputeRepository;
    private final ScreeningActivityRepository screeningActivityRepository;
    private final UserRepository userRepository;
    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final ComplianceLinksQueryService complianceLinksQueryService;
    private final MultiAssetCustomerRepository multiAssetCustomerRepository;
    private final EdgeNodeRepository edgeNodeRepository;
    private final PspRepository pspRepository;
    private final AssetAccountRepository assetAccountRepository;
    private final MessageRepository messageRepository;
    private final RuntimeErrorRepository runtimeErrorRepository;
    private final RegulatorySubmissionRepository regulatorySubmissionRepository;

    public ConsoleListExportQueryFacade(PspIsolationService pspIsolationService,
                                        AlertRepository alertRepository,
                                        ComplianceCaseRepository complianceCaseRepository,
                                        TransactionRepository transactionRepository,
                                        MerchantRepository merchantRepository,
                                        AuditLogRepository auditLogRepository,
                                        SuspiciousActivityReportRepository sarRepository,
                                        ChargebackDisputeRepository chargebackDisputeRepository,
                                        ScreeningActivityRepository screeningActivityRepository,
                                        UserRepository userRepository,
                                        RuleDefinitionRepository ruleDefinitionRepository,
                                        ComplianceLinksQueryService complianceLinksQueryService,
                                        MultiAssetCustomerRepository multiAssetCustomerRepository,
                                        EdgeNodeRepository edgeNodeRepository,
                                        PspRepository pspRepository,
                                        AssetAccountRepository assetAccountRepository,
                                        MessageRepository messageRepository,
                                        RuntimeErrorRepository runtimeErrorRepository,
                                        RegulatorySubmissionRepository regulatorySubmissionRepository) {
        this.pspIsolationService = pspIsolationService;
        this.alertRepository = alertRepository;
        this.complianceCaseRepository = complianceCaseRepository;
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.auditLogRepository = auditLogRepository;
        this.sarRepository = sarRepository;
        this.chargebackDisputeRepository = chargebackDisputeRepository;
        this.screeningActivityRepository = screeningActivityRepository;
        this.userRepository = userRepository;
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.complianceLinksQueryService = complianceLinksQueryService;
        this.multiAssetCustomerRepository = multiAssetCustomerRepository;
        this.edgeNodeRepository = edgeNodeRepository;
        this.pspRepository = pspRepository;
        this.assetAccountRepository = assetAccountRepository;
        this.messageRepository = messageRepository;
        this.runtimeErrorRepository = runtimeErrorRepository;
        this.regulatorySubmissionRepository = regulatorySubmissionRepository;
    }

    /** Returns how many primary rows match the export filters (used to choose sync vs async). */
    public long countRows(ConsoleListViewType viewType, Map<String, String> filters) {
        return switch (viewType) {
            case ALERTS -> alertRepository.count(alertSpec(filters));
            case CASES -> countCases(filters);
            case TRANSACTIONS -> transactionRepository.count(transactionSpec(filters));
            case MERCHANTS -> merchantRepository.count(merchantSpec(filters));
            case AUDIT_LOGS -> auditLogRepository.count(auditSpec(filters));
            case SARS -> countSars(filters);
            case CHARGEBACKS -> countChargebacks(filters);
            case SCREENING_ACTIVITY -> countScreening(filters);
            case USERS -> countUsers(filters);
            case RULES -> countRules(filters);
            case RECORD_DETAIL -> 1L;
            case MONITORING_TRANSACTIONS -> transactionRepository.count(transactionSpec(filters));
            case CUSTOMERS -> countCustomers(filters);
            case EDGE_NODES -> countEdgeNodes(filters);
            case PSPS -> countPsps(filters);
            case KYC_DOCUMENTS -> merchantRepository.count(merchantSpec(filters));
            case ASSET_ACCOUNTS -> countAssetAccounts(filters);
            case MESSAGES -> countMessages(filters);
            case RUNTIME_ERRORS -> countRuntimeErrors(filters);
            case REGULATORY_SUBMISSIONS -> countRegulatorySubmissions(filters);
        };
    }

    /** Fetches one page of export rows as column maps for CSV/PDF/XLSX writers. */
    public List<Map<String, Object>> fetchPage(ConsoleListViewType viewType,
                                             Map<String, String> filters,
                                             int page,
                                             int size,
                                             boolean includeCorrelated) {
        List<Map<String, Object>> rows = switch (viewType) {
            case ALERTS -> mapAlerts(alertRepository.findAll(alertSpec(filters),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
            case CASES -> mapCases(fetchCasePage(filters, page, size));
            case TRANSACTIONS -> mapTransactions(transactionRepository.findAll(transactionSpec(filters),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "txnTs"))).getContent());
            case MERCHANTS -> mapMerchants(merchantRepository.findAll(merchantSpec(filters),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
            case AUDIT_LOGS -> mapAudit(auditLogRepository.findAll(auditSpec(filters),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"))).getContent());
            case SARS -> mapSars(fetchSarPage(filters, page, size));
            case CHARGEBACKS -> mapChargebacks(fetchChargebackPage(filters, page, size));
            case SCREENING_ACTIVITY -> mapScreening(fetchScreeningPage(filters, page, size));
            case USERS -> mapUsers(fetchUserPage(filters, page, size));
            case RULES -> mapRules(fetchRulesPage(filters, page, size));
            case RECORD_DETAIL -> List.of();
            case MONITORING_TRANSACTIONS -> mapTransactions(transactionRepository.findAll(transactionSpec(filters),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "txnTs"))).getContent());
            case CUSTOMERS -> mapCustomers(fetchCustomerPage(filters, page, size));
            case EDGE_NODES -> mapEdgeNodes(fetchEdgeNodePage(filters, page, size));
            case PSPS -> mapPsps(fetchPspPage(filters, page, size));
            case KYC_DOCUMENTS -> mapMerchants(merchantRepository.findAll(merchantSpec(filters),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
            case ASSET_ACCOUNTS -> mapAssetAccounts(fetchAssetAccountPage(filters, page, size));
            case MESSAGES -> mapMessages(fetchMessagePage(filters, page, size));
            case RUNTIME_ERRORS -> mapRuntimeErrors(fetchRuntimeErrorPage(filters, page, size));
            case REGULATORY_SUBMISSIONS -> mapRegulatorySubmissions(fetchRegulatorySubmissionPage(filters, page, size));
        };
        if (!includeCorrelated || rows.isEmpty()) {
            return rows;
        }
        return appendCorrelatedRows(viewType, rows);
    }

    /** Builds correlated link rows appended after each primary export row when requested. */
    private List<Map<String, Object>> appendCorrelatedRows(ConsoleListViewType viewType,
                                                            List<Map<String, Object>> primary) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : primary) {
            out.add(row);
            ComplianceLinksResponse links = resolveLinks(viewType, row);
            if (links == null) {
                continue;
            }
            appendLinkSection(out, row, links.alerts(), "ALERT");
            appendLinkSection(out, row, links.cases(), "CASE");
            appendLinkSection(out, row, links.transactions(), "TRANSACTION");
            appendLinkSection(out, row, links.sars(), "SAR");
            appendLinkSection(out, row, links.disputes(), "DISPUTE");
            appendLinkSection(out, row, links.screeningActivities(), "SCREENING");
        }
        return out;
    }

    /** Resolves compliance links for a primary row based on view type and id columns. */
    private ComplianceLinksResponse resolveLinks(ConsoleListViewType viewType, Map<String, Object> row) {
        try {
            return switch (viewType) {
                case TRANSACTIONS -> complianceLinksQueryService.forTransaction(longVal(row.get("txnId")));
                case MERCHANTS -> complianceLinksQueryService.forMerchant(longVal(row.get("merchantId")));
                case ALERTS -> complianceLinksQueryService.forAlert(longVal(row.get("alertId")));
                case CASES -> complianceLinksQueryService.forCase(longVal(row.get("caseId")));
                case SARS -> complianceLinksQueryService.forSar(longVal(row.get("sarId")));
                case CHARGEBACKS -> complianceLinksQueryService.forDispute(longVal(row.get("disputeId")));
                case CUSTOMERS -> complianceLinksQueryService.forCustomer(longVal(row.get("customerId")));
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Copies linked records into export rows tagged as correlated children of a primary row. */
    private void appendLinkSection(List<Map<String, Object>> out,
                                   Map<String, Object> parent,
                                   List<ComplianceLinksResponse.LinkedRecord> links,
                                   String section) {
        if (links == null) {
            return;
        }
        for (ComplianceLinksResponse.LinkedRecord link : links) {
            Map<String, Object> correlated = new LinkedHashMap<>();
            correlated.put("rowKind", "CORRELATED");
            correlated.put("parentKey", parent.getOrDefault("exportKey", ""));
            correlated.put("linkSection", section);
            correlated.put("recordType", link.recordType());
            correlated.put("recordId", link.recordId());
            correlated.put("relationship", link.relationship());
            correlated.put("label", link.label());
            out.add(correlated);
        }
    }

    private static long longVal(Object o) {
        if (o == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(o));
    }

    private Specification<Alert> alertSpec(Map<String, String> filters) {
        Specification<Alert> spec = Specification.where(null);
        if (pspIsolationService.isPlatformAdministrator()) {
            // Super-admin exports are not PSP-scoped.
        } else {
            Long userPspId = pspIsolationService.getCurrentUserPspId();
            spec = spec.and((root, query, cb) -> {
                var subquery = query.subquery(Long.class);
                var merchantRoot = subquery.from(Merchant.class);
                subquery.select(merchantRoot.get("merchantId"))
                        .where(cb.and(
                                cb.equal(merchantRoot.get("merchantId"), root.get("merchantId")),
                                cb.equal(merchantRoot.get("psp").get("pspId"), userPspId)));
                return cb.or(cb.equal(root.get("pspId"), userPspId), cb.exists(subquery));
            });
        }
        String status = filters.get("status");
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        LocalDateTime[] range = dateRange(filters);
        if (range[0] != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), range[0]));
        }
        if (range[1] != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), range[1]));
        }
        return spec;
    }

    private Specification<TransactionEntity> transactionSpec(Map<String, String> filters) {
        Specification<TransactionEntity> spec = Specification.where(null);
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), pspId));
        }
        LocalDateTime[] range = dateRange(filters);
        if (range[0] != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("txnTs"), range[0]));
        }
        if (range[1] != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("txnTs"), range[1]));
        }
        return spec;
    }

    private Specification<Merchant> merchantSpec(Map<String, String> filters) {
        Specification<Merchant> spec = Specification.where(null);
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("psp").get("pspId"), pspId));
        }
        String status = filters.get("status");
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        String riskLevel = filters.get("riskLevel");
        if (riskLevel != null && !riskLevel.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("riskLevel"), riskLevel));
        }
        String q = filters.get("q");
        if (q == null || q.isBlank()) {
            q = filters.get("search");
        }
        if (q != null && !q.isBlank()) {
            String needle = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("legalName")), needle),
                    cb.like(cb.lower(root.get("tradingName")), needle)));
        }
        String kycStatus = filters.get("kycStatus");
        if (kycStatus != null && !kycStatus.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("kycStatus"), kycStatus));
        }
        return spec;
    }

    private Specification<AuditLog> auditSpec(Map<String, String> filters) {
        Specification<AuditLog> spec = Specification.where(null);
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), pspId));
        } else {
            String pspFilter = filters.get("pspId");
            if (pspFilter != null && !pspFilter.isBlank()) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), Long.parseLong(pspFilter)));
            }
        }
        LocalDateTime[] range = dateRange(filters);
        if (range[0] != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), range[0]));
        }
        if (range[1] != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), range[1]));
        }
        String actionType = filters.get("actionType");
        if (actionType != null && !actionType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actionType"), actionType));
        }
        String username = filters.get("username");
        if (username != null && !username.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("username"), username));
        }
        return spec;
    }

    /** Returns the PSP id exports must be scoped to, or null when the caller is a platform administrator. */
    private Long scopedPspId() {
        if (pspIsolationService.isPlatformAdministrator()) {
            return null;
        }
        return pspIsolationService.getCurrentUserPspId();
    }

    /** Parses start/end filter keys shared with the Console URL table state. */
    private LocalDateTime[] dateRange(Map<String, String> filters) {
        LocalDateTime start = parseDateTime(filters.get("start"), filters.get("startDate"));
        LocalDateTime end = parseDateTime(filters.get("end"), filters.get("endDate"));
        return new LocalDateTime[]{start, end};
    }

    /** Parses an ISO date-time or date-only filter value into LocalDateTime. */
    private LocalDateTime parseDateTime(String isoDateTime, String dateOnly) {
        if (isoDateTime != null && !isoDateTime.isBlank()) {
            return LocalDateTime.parse(isoDateTime);
        }
        if (dateOnly != null && !dateOnly.isBlank()) {
            return LocalDateTime.parse(dateOnly + "T00:00:00");
        }
        return null;
    }

    private long countCases(Map<String, String> filters) {
        String status = filters.get("status");
        Long pspId = scopedPspId();
        if (status != null && !status.isBlank()) {
            try {
                CaseStatus cs = CaseStatus.valueOf(status);
                if (pspId != null && pspId > 0) {
                    return complianceCaseRepository.countByPspIdAndStatus(pspId, cs);
                }
                return complianceCaseRepository.countByStatus(cs);
            } catch (IllegalArgumentException e) {
                return 0;
            }
        }
        if (pspId != null && pspId > 0) {
            return complianceCaseRepository.countByPspId(pspId);
        }
        return complianceCaseRepository.count();
    }

    private List<ComplianceCase> fetchCasePage(Map<String, String> filters, int page, int size) {
        String status = filters.get("status");
        Long pspId = scopedPspId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isBlank()) {
            try {
                CaseStatus cs = CaseStatus.valueOf(status);
                if (pspId != null && pspId > 0) {
                    return complianceCaseRepository.findByPspIdAndStatus(pspId, cs, pageable).getContent();
                }
                return complianceCaseRepository.findByStatus(cs, pageable).getContent();
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        if (pspId != null && pspId > 0) {
            return complianceCaseRepository.findByPspId(pspId, pageable).getContent();
        }
        return complianceCaseRepository.findAll(pageable).getContent();
    }

    private long countSars(Map<String, String> filters) {
        return pageTotalSars(filters);
    }

    /** Counts SAR rows using the same paging repository methods as list export. */
    private long pageTotalSars(Map<String, String> filters) {
        String status = filters.get("status");
        Long pspId = scopedPspId();
        boolean platform = pspIsolationService.isPlatformAdministrator();
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isBlank()) {
            try {
                SarStatus ss = SarStatus.valueOf(status);
                if (!platform && pspId != null) {
                    return sarRepository.findByPspIdAndStatus(pspId, ss, one).getTotalElements();
                }
                return sarRepository.findByStatus(ss, one).getTotalElements();
            } catch (IllegalArgumentException e) {
                return 0;
            }
        }
        if (!platform && pspId != null) {
            return sarRepository.findByPspId(pspId, one).getTotalElements();
        }
        return sarRepository.count();
    }

    private List<SuspiciousActivityReport> fetchSarPage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Long pspId = scopedPspId();
        boolean platform = pspIsolationService.isPlatformAdministrator();
        String status = filters.get("status");
        if (status != null && !status.isBlank()) {
            try {
                SarStatus ss = SarStatus.valueOf(status);
                if (!platform && pspId != null) {
                    return sarRepository.findByPspIdAndStatus(pspId, ss, pageable).getContent();
                }
                return sarRepository.findByStatus(ss, pageable).getContent();
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        if (!platform && pspId != null) {
            return sarRepository.findByPspId(pspId, pageable).getContent();
        }
        return sarRepository.findAll(pageable).getContent();
    }

    private long countChargebacks(Map<String, String> filters) {
        return fetchChargebackPage(filters, 0, 1).isEmpty()
                ? chargebackPageTotal(filters)
                : chargebackPageTotal(filters);
    }

    /** Counts chargeback rows using the same paging repository methods as list export. */
    private long chargebackPageTotal(Map<String, String> filters) {
        Long merchantId = parseLongFilter(filters.get("merchantId"));
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        Long pspId = scopedPspId();
        if (merchantId != null) {
            return chargebackDisputeRepository.findByMerchantId(merchantId, one).getTotalElements();
        }
        if (pspId != null && pspId > 0) {
            return chargebackDisputeRepository.findByPspId(pspId, one).getTotalElements();
        }
        return chargebackDisputeRepository.count();
    }

    private List<ChargebackDispute> fetchChargebackPage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Long merchantId = parseLongFilter(filters.get("merchantId"));
        Long pspId = scopedPspId();
        if (merchantId != null) {
            return chargebackDisputeRepository.findByMerchantId(merchantId, pageable).getContent();
        }
        if (pspId != null && pspId > 0) {
            return chargebackDisputeRepository.findByPspId(pspId, pageable).getContent();
        }
        return chargebackDisputeRepository.findAll(pageable).getContent();
    }

    private long countScreening(Map<String, String> filters) {
        Long txnId = parseLongFilter(filters.get("txnId"));
        Long merchantId = parseLongFilter(filters.get("merchantId"));
        if (txnId != null) {
            return screeningActivityRepository.findByTxnIdOrderByCreatedAtDesc(txnId, PageRequest.of(0, 1)).getTotalElements();
        }
        if (merchantId != null) {
            return screeningActivityRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, PageRequest.of(0, 1)).getTotalElements();
        }
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return screeningActivityRepository.findByPspIdOrderByCreatedAtDesc(pspId, PageRequest.of(0, 1)).getTotalElements();
        }
        return screeningActivityRepository.count();
    }

    private List<ScreeningActivity> fetchScreeningPage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Long txnId = parseLongFilter(filters.get("txnId"));
        Long merchantId = parseLongFilter(filters.get("merchantId"));
        if (txnId != null) {
            return screeningActivityRepository.findByTxnIdOrderByCreatedAtDesc(txnId, pageable).getContent();
        }
        if (merchantId != null) {
            return screeningActivityRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable).getContent();
        }
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return screeningActivityRepository.findByPspIdOrderByCreatedAtDesc(pspId, pageable).getContent();
        }
        return screeningActivityRepository.findAll(pageable).getContent();
    }

    private long countUsers(Map<String, String> filters) {
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return userRepository.count(userPspSpec(pspId));
        }
        return userRepository.count();
    }

    private List<User> fetchUserPage(Map<String, String> filters, int page, int size) {
        Long pspId = scopedPspId();
        List<User> all;
        if (pspId != null && pspId > 0) {
            all = userRepository.findAll(userPspSpec(pspId));
        } else {
            all = userRepository.findAll();
        }
        return all.stream().skip((long) page * size).limit(size).toList();
    }

    /** Builds a JPA spec restricting users to one PSP tenant. */
    private Specification<User> userPspSpec(Long pspId) {
        return (root, query, cb) -> cb.equal(root.get("psp").get("pspId"), pspId);
    }

    private long countRules(Map<String, String> filters) {
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return ruleDefinitionRepository.findVisibleForPsp(pspId).size();
        }
        return ruleDefinitionRepository.count();
    }

    private List<RuleDefinition> fetchRulesPage(Map<String, String> filters, int page, int size) {
        Long pspId = scopedPspId();
        List<RuleDefinition> all = pspId != null && pspId > 0
                ? ruleDefinitionRepository.findVisibleForPsp(pspId)
                : ruleDefinitionRepository.findAll();
        return sliceList(all, page, size);
    }

    private static <T> List<T> sliceList(List<T> all, int page, int size) {
        return all.stream().skip((long) page * size).limit(size).toList();
    }

    private static Long parseLongFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Long.parseLong(raw);
    }

    private List<Map<String, Object>> mapAlerts(List<Alert> alerts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Alert a : alerts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "ALERT:" + a.getAlertId());
            row.put("alertId", a.getAlertId());
            row.put("severity", a.getSeverity());
            row.put("action", a.getAction());
            row.put("status", a.getStatus());
            row.put("description", a.getReason());
            row.put("transactionId", a.getTxnId());
            row.put("caseId", a.getComplianceCaseId());
            row.put("createdAt", a.getCreatedAt() != null ? ISO.format(a.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapCases(List<ComplianceCase> cases) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ComplianceCase c : cases) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "CASE:" + c.getId());
            row.put("caseId", c.getId());
            row.put("caseReference", c.getCaseReference());
            row.put("status", c.getStatus() != null ? c.getStatus().name() : "");
            row.put("priority", c.getPriority() != null ? c.getPriority().name() : "");
            row.put("merchantId", c.getMerchantId());
            row.put("pspId", c.getPspId());
            row.put("createdAt", c.getCreatedAt() != null ? ISO.format(c.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapTransactions(List<TransactionEntity> txns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TransactionEntity t : txns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "TRANSACTION:" + t.getTxnId());
            row.put("txnId", t.getTxnId());
            row.put("merchantId", t.getMerchantId());
            row.put("amountCents", t.getAmountCents());
            row.put("currency", t.getCurrency());
            row.put("decision", t.getDecision());
            row.put("pspId", t.getPspId());
            row.put("txnTs", t.getTxnTs() != null ? ISO.format(t.getTxnTs()) : "");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapMerchants(List<Merchant> merchants) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Merchant m : merchants) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "MERCHANT:" + m.getMerchantId());
            row.put("merchantId", m.getMerchantId());
            row.put("legalName", m.getLegalName());
            row.put("status", m.getStatus());
            row.put("riskLevel", m.getRiskLevel());
            row.put("pspId", m.getPsp() != null ? m.getPsp().getPspId() : null);
            row.put("createdAt", m.getCreatedAt() != null ? ISO.format(m.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapAudit(List<AuditLog> logs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AuditLog l : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "AUDIT:" + l.getId());
            row.put("id", l.getId());
            row.put("timestamp", l.getTimestamp() != null ? ISO.format(l.getTimestamp()) : "");
            row.put("username", l.getUsername());
            row.put("actionType", l.getActionType());
            row.put("entityType", l.getEntityType());
            row.put("entityId", l.getEntityId());
            row.put("pspId", l.getPspId());
            row.put("success", l.isSuccess());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapSars(List<SuspiciousActivityReport> sars) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SuspiciousActivityReport s : sars) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "SAR:" + s.getId());
            row.put("sarId", s.getId());
            row.put("sarReference", s.getSarReference());
            row.put("status", s.getStatus() != null ? s.getStatus().name() : "");
            row.put("jurisdiction", s.getJurisdiction());
            row.put("pspId", s.getPspId());
            row.put("createdAt", s.getCreatedAt() != null ? ISO.format(s.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapChargebacks(List<ChargebackDispute> disputes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChargebackDispute d : disputes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "DISPUTE:" + d.getId());
            row.put("disputeId", d.getId());
            row.put("merchantId", d.getMerchantId());
            row.put("alertId", d.getAlertId());
            row.put("notificationType", d.getNotificationType());
            row.put("pspId", d.getPspId());
            row.put("createdAt", d.getCreatedAt() != null ? ISO.format(d.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapScreening(List<ScreeningActivity> activities) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScreeningActivity sa : activities) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "SCREENING:" + sa.getId());
            row.put("activityId", sa.getId());
            row.put("txnId", sa.getTxnId());
            row.put("merchantId", sa.getMerchantId());
            row.put("reason", sa.getReason());
            row.put("hit", sa.isHit());
            row.put("blocking", sa.isBlocking());
            row.put("pspId", sa.getPspId());
            row.put("createdAt", sa.getCreatedAt() != null ? ISO.format(sa.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapUsers(List<User> users) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "USER:" + u.getId());
            row.put("userId", u.getId());
            row.put("username", u.getUsername());
            row.put("email", u.getEmail());
            row.put("role", u.getRole() != null ? u.getRole().getName() : "");
            row.put("pspId", u.getPsp() != null ? u.getPsp().getPspId() : null);
            row.put("enabled", u.isEnabled());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> mapRules(List<RuleDefinition> rules) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RuleDefinition r : rules) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "RULE:" + r.getId());
            row.put("ruleId", r.getId());
            row.put("name", r.getName());
            row.put("enabled", r.isEnabled());
            row.put("priority", r.getPriority());
            row.put("pspId", r.getPspId());
            row.put("systemManaged", r.isSystemManaged());
            rows.add(row);
        }
        return rows;
    }

    /** Counts multi-asset customers using the same search keys as Customer360 URL state. */
    private long countCustomers(Map<String, String> filters) {
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "displayName"));
        return customerPage(filters, one).getTotalElements();
    }

    /** Builds a customer page query with PSP isolation and optional display-name search. */
    private Page<MultiAssetCustomer> customerPage(Map<String, String> filters, PageRequest pageable) {
        Long pspId = scopedPspId();
        String search = customerSearch(filters);
        if (pspId != null && pspId > 0) {
            if (search == null) {
                return multiAssetCustomerRepository.findByPspId(pspId, pageable);
            }
            return multiAssetCustomerRepository.findByPspIdAndDisplayNameContainingIgnoreCase(pspId, search, pageable);
        }
        if (search == null) {
            return multiAssetCustomerRepository.findAll(pageable);
        }
        return multiAssetCustomerRepository.findByDisplayNameContainingIgnoreCase(search, pageable);
    }

    /** Fetches one page of multi-asset customers for export. */
    private List<MultiAssetCustomer> fetchCustomerPage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayName"));
        return customerPage(filters, pageable).getContent();
    }

    /** Reads the customer search filter from Console URL keys. */
    private static String customerSearch(Map<String, String> filters) {
        String search = filters.get("search");
        if (search == null || search.isBlank()) {
            search = filters.get("q");
        }
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }

    /** Counts edge nodes visible to the current PSP tenant. */
    private long countEdgeNodes(Map<String, String> filters) {
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        return edgeNodePage(filters, one).getTotalElements();
    }

    /** Fetches one page of edge node registry rows for export. */
    private List<EdgeNode> fetchEdgeNodePage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return edgeNodePage(filters, pageable).getContent();
    }

    /** Resolves edge node listing with PSP isolation matching the Edge Nodes Console page. */
    private Page<EdgeNode> edgeNodePage(Map<String, String> filters, PageRequest pageable) {
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            List<EdgeNode> all = edgeNodeRepository.findByPspIdOrderByCreatedAtDesc(pspId);
            return new org.springframework.data.domain.PageImpl<>(sliceList(all, pageable.getPageNumber(), pageable.getPageSize()),
                    pageable, all.size());
        }
        List<EdgeNode> all = edgeNodeRepository.findAllByOrderByCreatedAtDesc();
        return new org.springframework.data.domain.PageImpl<>(sliceList(all, pageable.getPageNumber(), pageable.getPageSize()),
                pageable, all.size());
    }

    /** Counts PSP rows the caller is allowed to export. */
    private long countPsps(Map<String, String> filters) {
        return fetchPspPage(filters, 0, 1).isEmpty() ? pspPageTotal(filters) : pspPageTotal(filters);
    }

    /** Returns total PSP rows for export row-count estimation. */
    private long pspPageTotal(Map<String, String> filters) {
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return pspRepository.existsById(pspId) ? 1 : 0;
        }
        return pspRepository.count();
    }

    /** Fetches one page of PSP configuration rows for export. */
    private List<Psp> fetchPspPage(Map<String, String> filters, int page, int size) {
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return pspRepository.findById(pspId).map(List::of).orElse(List.of());
        }
        return sliceList(pspRepository.findAll(), page, size);
    }

    /** Counts asset accounts for the active PSP tenant or platform-wide for administrators. */
    private long countAssetAccounts(Map<String, String> filters) {
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        return assetAccountPage(filters, one).getTotalElements();
    }

    /** Fetches one page of asset accounts for export. */
    private List<AssetAccount> fetchAssetAccountPage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return assetAccountPage(filters, pageable).getContent();
    }

    /** Resolves asset account paging with PSP isolation. */
    private Page<AssetAccount> assetAccountPage(Map<String, String> filters, PageRequest pageable) {
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return assetAccountRepository.findByPspIdOrderByCreatedAtDesc(pspId, pageable);
        }
        return assetAccountRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** Counts in-app messages for the recipient encoded in export filters. */
    private long countMessages(Map<String, String> filters) {
        Long recipientUserId = parseLongFilter(filters.get("recipientUserId"));
        if (recipientUserId == null) {
            return 0;
        }
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        return messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, one).getTotalElements();
    }

    /** Fetches one page of messages for export for the authenticated recipient. */
    private List<Message> fetchMessagePage(Map<String, String> filters, int page, int size) {
        Long recipientUserId = parseLongFilter(filters.get("recipientUserId"));
        if (recipientUserId == null) {
            return List.of();
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId, pageable).getContent();
    }

    /** Counts runtime error rows with PSP isolation. */
    private long countRuntimeErrors(Map<String, String> filters) {
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return runtimeErrorPage(one).getTotalElements();
    }

    /** Fetches one page of runtime errors for export. */
    private List<RuntimeError> fetchRuntimeErrorPage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return runtimeErrorPage(pageable).getContent();
    }

    /** Resolves runtime error paging with PSP isolation. */
    private Page<RuntimeError> runtimeErrorPage(PageRequest pageable) {
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return runtimeErrorRepository.findByPspIdOrderByOccurredAtDesc(pspId, pageable);
        }
        return runtimeErrorRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    /** Counts regulatory submission rows using Console URL filter keys. */
    private long countRegulatorySubmissions(Map<String, String> filters) {
        PageRequest one = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        return regulatorySubmissionPage(filters, one).getTotalElements();
    }

    /** Fetches one page of regulatory submissions for export. */
    private List<RegulatorySubmission> fetchRegulatorySubmissionPage(Map<String, String> filters, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return regulatorySubmissionPage(filters, pageable).getContent();
    }

    /** Resolves regulatory submission paging with PSP isolation and status/regulator filters. */
    private Page<RegulatorySubmission> regulatorySubmissionPage(Map<String, String> filters, PageRequest pageable) {
        Long pspId = scopedPspId();
        Long filterPsp = pspId != null && pspId > 0 ? pspId : null;
        SubmissionStatus status = parseSubmissionStatus(filters.get("status"));
        String regulator = blankToNull(filters.get("regulator"));
        return regulatorySubmissionRepository.findByFilters(filterPsp, status, regulator, null, null, pageable);
    }

    /** Parses a regulatory submission status filter safely. */
    private static SubmissionStatus parseSubmissionStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SubmissionStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Treats blank filter strings as SQL NULL equivalents for optional query params. */
    private static String blankToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    /** Maps multi-asset customers into export column rows. */
    private List<Map<String, Object>> mapCustomers(List<MultiAssetCustomer> customers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MultiAssetCustomer c : customers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "CUSTOMER:" + c.getId());
            row.put("customerId", c.getId());
            row.put("externalCustomerId", c.getExternalCustomerId());
            row.put("displayName", c.getDisplayName());
            row.put("customerType", c.getCustomerType());
            row.put("riskTier", c.getRiskTier());
            row.put("kycStatus", c.getKycStatus());
            row.put("pspId", c.getPspId());
            row.put("createdAt", c.getCreatedAt() != null ? ISO.format(c.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    /** Maps edge node registry rows into export column rows. */
    private List<Map<String, Object>> mapEdgeNodes(List<EdgeNode> nodes) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EdgeNode node : nodes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "EDGE_NODE:" + node.getId());
            row.put("edgeNodeId", node.getId());
            row.put("edgeId", node.getEdgeId());
            row.put("status", node.getStatus() != null ? node.getStatus().name() : "");
            row.put("pspId", node.getPspId());
            row.put("createdAt", node.getCreatedAt() != null ? node.getCreatedAt().toString() : "");
            rows.add(row);
        }
        return rows;
    }

    /** Maps PSP rows into export column rows. */
    private List<Map<String, Object>> mapPsps(List<Psp> psps) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Psp p : psps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "PSP:" + p.getPspId());
            row.put("pspId", p.getPspId());
            row.put("pspCode", p.getPspCode());
            row.put("legalName", p.getLegalName());
            row.put("status", p.getStatus());
            row.put("country", p.getCountry());
            rows.add(row);
        }
        return rows;
    }

    /** Maps asset account rows into export column rows. */
    private List<Map<String, Object>> mapAssetAccounts(List<AssetAccount> accounts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetAccount account : accounts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "ASSET_ACCOUNT:" + account.getId());
            row.put("accountId", account.getId());
            row.put("customerId", account.getCustomer() != null ? account.getCustomer().getId() : null);
            row.put("externalAccountId", account.getExternalAccountId());
            row.put("assetClass", account.getAssetClass() != null ? account.getAssetClass().name() : "");
            row.put("accountType", account.getAccountType() != null ? account.getAccountType().name() : "");
            row.put("status", account.getStatus());
            row.put("pspId", account.getPspId());
            row.put("createdAt", account.getCreatedAt() != null ? ISO.format(account.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    /** Maps in-app messages into export column rows. */
    private List<Map<String, Object>> mapMessages(List<Message> messages) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "MESSAGE:" + message.getId());
            row.put("messageId", message.getId());
            row.put("subject", message.getSubject());
            row.put("category", message.getCategory() != null ? message.getCategory().name() : "");
            row.put("readAt", message.getReadAt() != null ? ISO.format(message.getReadAt()) : "");
            row.put("createdAt", message.getCreatedAt() != null ? ISO.format(message.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    /** Maps runtime error rows into export column rows. */
    private List<Map<String, Object>> mapRuntimeErrors(List<RuntimeError> errors) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RuntimeError error : errors) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "RUNTIME_ERROR:" + error.getId());
            row.put("errorId", error.getId());
            row.put("errorCode", error.getErrorCode());
            row.put("message", error.getMessage());
            row.put("pspId", error.getPspId());
            row.put("occurredAt", error.getOccurredAt() != null ? ISO.format(error.getOccurredAt()) : "");
            rows.add(row);
        }
        return rows;
    }

    /** Maps regulatory submission rows into export column rows. */
    private List<Map<String, Object>> mapRegulatorySubmissions(List<RegulatorySubmission> submissions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RegulatorySubmission submission : submissions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exportKey", "REGULATORY_SUBMISSION:" + submission.getId());
            row.put("submissionId", submission.getId());
            row.put("submissionReference", submission.getSubmissionReference());
            row.put("status", submission.getStatus() != null ? submission.getStatus().name() : "");
            row.put("regulatorCode", submission.getRegulatorCode());
            row.put("pspId", submission.getPspId());
            row.put("filingDeadline", submission.getFilingDeadline() != null ? submission.getFilingDeadline().toString() : "");
            row.put("createdAt", submission.getCreatedAt() != null ? ISO.format(submission.getCreatedAt()) : "");
            rows.add(row);
        }
        return rows;
    }
}
