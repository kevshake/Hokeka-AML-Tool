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
import com.posgateway.aml.entity.merchant.Merchant;
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
                                        ComplianceLinksQueryService complianceLinksQueryService) {
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
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("pspId"), pspId));
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
        Long pspId = scopedPspId();
        if (pspId != null && pspId > 0) {
            return chargebackDisputeRepository.findByPspIdOrderByCreatedAtDesc(pspId).size();
        }
        return chargebackDisputeRepository.count();
    }

    private List<ChargebackDispute> fetchChargebackPage(Map<String, String> filters, int page, int size) {
        Long pspId = scopedPspId();
        List<ChargebackDispute> all = pspId != null && pspId > 0
                ? chargebackDisputeRepository.findByPspIdOrderByCreatedAtDesc(pspId)
                : chargebackDisputeRepository.findAll();
        return all.stream().skip((long) page * size).limit(size).toList();
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
}
