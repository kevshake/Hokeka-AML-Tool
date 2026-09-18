package com.posgateway.aml.service;

import com.posgateway.aml.entity.AuditLog;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.SarStatus;
import com.posgateway.aml.repository.AuditLogRepository;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private final ComplianceCaseRepository caseRepository;
    private final SuspiciousActivityReportRepository sarRepository;
    private final AuditLogRepository auditLogRepository;
    private final com.posgateway.aml.repository.MerchantRepository merchantRepository;
    private final PspIsolationService pspIsolationService;

    public ReportingService(ComplianceCaseRepository caseRepository,
            SuspiciousActivityReportRepository sarRepository,
            AuditLogRepository auditLogRepository,
            com.posgateway.aml.repository.MerchantRepository merchantRepository,
            PspIsolationService pspIsolationService) {
        this.caseRepository = caseRepository;
        this.sarRepository = sarRepository;
        this.auditLogRepository = auditLogRepository;
        this.merchantRepository = merchantRepository;
        this.pspIsolationService = pspIsolationService;
    }

    private Long getCurrentPspScope() {
        com.posgateway.aml.entity.User user = pspIsolationService.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return pspIsolationService.isPlatformAdministrator(user)
                ? null
                : pspIsolationService.getCurrentUserPspId();
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new HashMap<>();
        Long pspId = getCurrentPspScope();

        result.put("casesByStatus", casesByStatus());
        result.put("sarsByStatus", sarsByStatus());
        
        // Merchant Statistics
        if (pspId != null) {
            result.put("totalMerchants", merchantRepository.countByPspPspId(pspId));
            result.put("activeMerchants", merchantRepository.countByPspPspIdAndStatus(pspId, "ACTIVE"));
            result.put("highRiskMerchants",
                    merchantRepository.countByPspPspIdAndRiskLevel(pspId, "HIGH")
                            + merchantRepository.countByPspPspIdAndRiskLevel(pspId, "CRITICAL"));
        } else {
            result.put("totalMerchants", merchantRepository.count());
            result.put("activeMerchants", merchantRepository.countByStatus("ACTIVE"));
            result.put("highRiskMerchants",
                    merchantRepository.countByRiskLevel("HIGH") + merchantRepository.countByRiskLevel("CRITICAL"));
        }

        // Total Volume (Sum of expected volume)
        java.util.List<com.posgateway.aml.entity.merchant.Merchant> merchants = (pspId != null)
                ? merchantRepository.findByPspPspId(pspId)
                : merchantRepository.findAll();
        long totalExpectedVolume = merchants.stream()
                .filter(m -> m.getExpectedMonthlyVolume() != null)
                .mapToLong(com.posgateway.aml.entity.merchant.Merchant::getExpectedMonthlyVolume)
                .sum();
        result.put("totalVolume", totalExpectedVolume);

        result.put("auditLast24h", auditCountLastHours(24));
        result.put("casesLast7d", dailyCountsCases(7));
        result.put("sarsLast7d", dailyCountsSars(7));
        return result;
    }

    public Map<String, Long> auditHourly(int hoursBack) {
        int boundedHours = Math.max(1, Math.min(hoursBack, 24 * 31));
        Long pspId = getCurrentPspScope();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(boundedHours);
        List<AuditLog> logs = pspId == null
                ? auditLogRepository.findByTimestampBetween(start, now)
                : auditLogRepository.findByPspIdAndTimestampBetween(pspId, start, now);
        Map<String, Long> buckets = new LinkedHashMap<>();
        for (int i = boundedHours - 1; i >= 0; i--) {
            LocalDateTime bucketStart = now.minusHours(i + 1);
            LocalDateTime bucketEnd = now.minusHours(i);
            long count = logs.stream()
                    .filter(l -> !l.getTimestamp().isBefore(bucketStart) && l.getTimestamp().isBefore(bucketEnd))
                    .count();
            buckets.put(bucketStart.toLocalTime().withMinute(0).withSecond(0).withNano(0).toString(), count);
        }
        return buckets;
    }

    public Map<CaseStatus, Long> casesByStatus() {
        Long pspId = getCurrentPspScope();
        Map<CaseStatus, Long> map = new EnumMap<>(CaseStatus.class);
        for (CaseStatus status : CaseStatus.values()) {
            if (pspId != null) {
                map.put(status, caseRepository.countByPspIdAndStatus(pspId, status));
            } else {
                map.put(status, caseRepository.countByStatus(status));
            }
        }
        return map;
    }

    public Map<String, Long> casesStatusPriorityMatrix() {
        Long pspId = getCurrentPspScope();
        Map<String, Long> result = new LinkedHashMap<>();
        for (CaseStatus s : CaseStatus.values()) {
            for (com.posgateway.aml.model.CasePriority p : com.posgateway.aml.model.CasePriority.values()) {
                long c;
                if (pspId != null) {
                    c = caseRepository.countByPspIdAndStatusAndPriority(pspId, s, p);
                } else {
                    c = caseRepository.countByStatusAndPriority(s, p);
                }
                result.put(s.name() + "|" + p.name(), c);
            }
        }
        return result;
    }

    public Map<SarStatus, Long> sarsByStatus() {
        Long pspId = getCurrentPspScope();
        Map<SarStatus, Long> map = new EnumMap<>(SarStatus.class);
        for (SarStatus status : SarStatus.values()) {
            if (pspId != null) {
                map.put(status, sarRepository.countByPspIdAndStatus(pspId, status));
            } else {
                map.put(status, sarRepository.countByStatus(status));
            }
        }
        return map;
    }

    public Map<String, Long> dailyCountsCases(int daysBack) {
        return dailyCountsCases(daysBack, null);
    }

    public Map<String, Long> dailyCountsCases(int daysBack, String merchantId) {
        int boundedDays = Math.max(1, Math.min(daysBack, 366));
        Long pspId = getCurrentPspScope();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(boundedDays - 1L).atStartOfDay();
        List<ComplianceCase> cases;

        if (pspId != null) {
            cases = caseRepository.findByPspIdAndCreatedAtBetween(pspId, start, LocalDateTime.now());
            if (merchantId != null && !merchantId.isEmpty()) {
                cases = cases.stream().filter(c -> merchantId.equals(c.getMerchantId())).toList();
            }
        } else {
            if (merchantId != null && !merchantId.isEmpty()) {
                cases = caseRepository.findByCreatedAtBetween(start, LocalDateTime.now()).stream()
                        .filter(c -> merchantId.equals(c.getMerchantId()))
                        .toList();
            } else {
                cases = caseRepository.findByCreatedAtBetween(start, LocalDateTime.now());
            }
        }
        return cases.stream()
                .collect(Collectors.groupingBy(c -> c.getCreatedAt().toLocalDate().toString(), Collectors.counting()));
    }

    public Map<String, Long> dailyCountsSars(int daysBack) {
        int boundedDays = Math.max(1, Math.min(daysBack, 366));
        Long pspId = getCurrentPspScope();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(boundedDays - 1L).atStartOfDay();
        List<SuspiciousActivityReport> sars;

        if (pspId != null) {
            sars = sarRepository.findByPspIdAndCreatedAtBetween(pspId, start, LocalDateTime.now());
        } else {
            sars = sarRepository.findByCreatedAtBetween(start, LocalDateTime.now());
        }

        return sars.stream()
                .collect(Collectors.groupingBy(s -> s.getCreatedAt().toLocalDate().toString(), Collectors.counting()));
    }

    public long auditCountLastHours(int hours) {
        int boundedHours = Math.max(1, Math.min(hours, 24 * 31));
        Long pspId = getCurrentPspScope();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(boundedHours);
        return pspId == null
                ? auditLogRepository.countByTimestampAfter(start)
                : auditLogRepository.countByPspIdAndTimestampAfter(pspId, start);
    }
}
