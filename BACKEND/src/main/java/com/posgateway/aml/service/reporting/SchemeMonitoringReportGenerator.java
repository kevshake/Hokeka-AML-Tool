package com.posgateway.aml.service.reporting;

import com.posgateway.aml.repository.AerospikeMetricsRepository;
import com.posgateway.aml.service.risk.SchemeSimulatorService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheme Monitoring Report Generator
 * Generates VFMP and HECM risk reports
 * Note: In a real system, we'd iterate over all merchants for a PSP.
 * Since Aerospike K-V doesn't easily iterate all keys without a scan,
 * we will simulate this by accepting a list of merchant IDs or just returning
 * headers for now
 * if no merchant list service is injected.
 * 
 * Ideally, we should inject MerchantRepository to get list of merchants for the
 * PSP.
 */
@Component
public class SchemeMonitoringReportGenerator implements ReportGenerator {

    @SuppressWarnings("unused")
    private final AerospikeMetricsRepository metricsRepository;
    private final SchemeSimulatorService schemeSimulatorService;
    // Assuming we have a way to find merchants for a PSP.
    // For this implementation, we will mock/placeholder the merchant iteration
    // or rely on an injected MerchantRepository (using full path to avoid
    // collision/import error if not open).
    private final com.posgateway.aml.repository.MerchantRepository merchantRepository;

    public SchemeMonitoringReportGenerator(AerospikeMetricsRepository metricsRepository,
            SchemeSimulatorService schemeSimulatorService,
            com.posgateway.aml.repository.MerchantRepository merchantRepository) {
        this.metricsRepository = metricsRepository;
        this.schemeSimulatorService = schemeSimulatorService;
        this.merchantRepository = merchantRepository;
    }

    @Override
    public Map<String, Object> generate(Long pspId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> report = new HashMap<>();
        List<Map<String, Object>> merchantRisks = new ArrayList<>();

        // 1. Get all merchants for this PSP
        List<com.posgateway.aml.entity.merchant.Merchant> merchants = merchantRepository.findByPspPspId(pspId);

        // 2. Evaluate Risk for each
        for (com.posgateway.aml.entity.merchant.Merchant merchant : merchants) {
            com.posgateway.aml.service.risk.SchemeSimulatorService.MerchantRiskAssessment assessment = schemeSimulatorService
                    .assessMerchant(String.valueOf(merchant.getMerchantId()));

            if (assessment.isHighRisk()) {
                Map<String, Object> row = new HashMap<>();
                row.put("merchant_id", merchant.getMerchantId());
                row.put("merchant_name", merchant.getLegalName());
                row.put("vfmp_stage", assessment.getVfmpResult().getStage());
                row.put("hecm_stage", assessment.getHecmResult().getStage());
                row.put("fraud_rate", assessment.getVfmpResult().getFraudRate());
                row.put("cb_ratio", assessment.getHecmResult().getRatio());
                merchantRisks.add(row);
            }
        }

        report.put("high_risk_merchants", merchantRisks);
        report.put("total_merchants_scanned", merchants.size());
        report.put("generated_date", LocalDate.now());

        return report;
    }

    @Override
    public String getType() {
        return "SCHEME_MONITORING";
    }
}
