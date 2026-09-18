package com.posgateway.aml.service.reporting;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CentralBankReportGenerator implements ReportGenerator {

    private final ComplianceCaseRepository caseRepository;
    private final SuspiciousActivityReportRepository sarRepository;

    public CentralBankReportGenerator(ComplianceCaseRepository caseRepository,
            SuspiciousActivityReportRepository sarRepository) {
        this.caseRepository = caseRepository;
        this.sarRepository = sarRepository;
    }

    @Override
    public Map<String, Object> generate(Long pspId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> report = new HashMap<>();

        // 1. Fetch Cases
        List<ComplianceCase> cases = caseRepository.findByPspIdAndCreatedAtBetween(
                pspId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

        // 2. Fetch SARs
        List<SuspiciousActivityReport> sars = sarRepository.findByPspIdAndCreatedAtBetween(
                pspId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));

        // 3. Aggregate Data
        report.put("total_cases", cases.size());
        report.put("total_sars", sars.size());

        // Group by Status
        Map<String, Long> casesByStatus = cases.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus().name(), Collectors.counting()));
        report.put("cases_by_status", casesByStatus);

        // List of SARs (Simplified for report)
        List<Map<String, Object>> sarList = sars.stream().map(sar -> {
            Map<String, Object> item = new HashMap<>();
            item.put("sar_id", sar.getId());
            item.put("merchant_id", sar.getComplianceCase() != null && sar.getComplianceCase().getMerchantId() != null ? sar.getComplianceCase().getMerchantId().toString() : "N/A");
            item.put("created_at", sar.getCreatedAt());
            item.put("status", sar.getStatus());
            return item;
        }).collect(Collectors.toList());

        report.put("sar_details", sarList);
        report.put("generated_date", LocalDate.now());
        report.put("psp_id", pspId);

        return report;
    }

    @Override
    public String getType() {
        return "CENTRAL_BANK";
    }
}
