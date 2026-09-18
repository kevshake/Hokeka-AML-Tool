package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.record.RecordTrailDtos.RecordLink;
import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.entity.reporting.Report;
import com.posgateway.aml.entity.reporting.ReportExecution;
import com.posgateway.aml.entity.reporting.TriggerType;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.service.record.RecordTrailService;
import com.posgateway.aml.service.security.PspIsolationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persists provenance for synchronous report endpoints that do not use ReportGenerationService. */
@Service
public class ReportRunTraceService {
    private static final int RECORD_LINK_LIMIT = 500;

    private final ReportRepository reportRepository;
    private final ReportExecutionRepository executionRepository;
    private final RecordTrailService recordTrailService;
    private final PspIsolationService pspIsolationService;

    public ReportRunTraceService(ReportRepository reportRepository,
                                 ReportExecutionRepository executionRepository,
                                 RecordTrailService recordTrailService,
                                 PspIsolationService pspIsolationService) {
        this.reportRepository = reportRepository;
        this.executionRepository = executionRepository;
        this.recordTrailService = recordTrailService;
        this.pspIsolationService = pspIsolationService;
    }

    @Transactional
    public String record(String reportCode, LocalDateTime dateFrom, LocalDateTime dateTo,
                         List<Map<String, Object>> rows, Map<String, Object> additionalSummary) {
        Report report = reportRepository.findByReportCode(reportCode)
                .orElseThrow(() -> new IllegalStateException("No report registry entry for " + reportCode));
        Long pspId = pspIsolationService.getCurrentUserPspId();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, Map<String, Object>> links = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    totals.merge(entry.getKey(), new BigDecimal(number.toString()), BigDecimal::add);
                }
                recordTypeForColumn(entry.getKey()).ifPresent(type -> addLink(links, type, entry.getValue()));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rowCount", rows.size());
        summary.put("numericTotals", totals);
        summary.put("calculatedAt", LocalDateTime.now());
        if (additionalSummary != null) summary.putAll(additionalSummary);

        ReportExecution execution = new ReportExecution();
        execution.setReport(report);
        execution.setExecutionId("DIRECT_" + reportCode + "_" + UUID.randomUUID());
        execution.setPspId(pspId);
        execution.setTriggerType(TriggerType.API);
        execution.setParameters(Map.of("source", "DIRECT_REPORT_ENDPOINT", "reportCode", reportCode));
        execution.setSourceContext(Map.of(
                "source", "DIRECT_REPORT_ENDPOINT",
                "reportCode", reportCode,
                "dateFrom", dateFrom.toString(),
                "dateTo", dateTo.toString()));
        execution.setDateFrom(dateFrom);
        execution.setDateTo(dateTo);
        execution.setCalculationSummary(summary);
        execution.setRecordLinks(List.copyOf(links.values()));
        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setProgressPercent(100);
        execution.setTotalRecords((long) rows.size());
        execution.setStartedAt(LocalDateTime.now());
        execution.setCompletedAt(LocalDateTime.now());
        return executionRepository.save(execution).getExecutionId();
    }

    public String recordDocument(String reportCode, LocalDateTime dateFrom, LocalDateTime dateTo,
                                 Map<String, Object> document) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        document.forEach((key, value) -> {
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        map.forEach((entryKey, entryValue) -> row.put(String.valueOf(entryKey), entryValue));
                        rows.add(row);
                    }
                }
            } else {
                summary.put(key, value);
            }
        });
        return record(reportCode, dateFrom, dateTo, rows, summary);
    }

    private void addLink(Map<String, Map<String, Object>> links, String type, Object id) {
        if (id == null || links.size() >= RECORD_LINK_LIMIT) return;
        recordTrailService.resolveLink(type, id).ifPresent(link -> links.putIfAbsent(type + ":" + link.recordId(), linkMap(link)));
    }

    private Map<String, Object> linkMap(RecordLink link) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("recordType", link.recordType());
        value.put("recordId", link.recordId());
        value.put("label", link.label());
        value.put("relationship", "REPORT_ROW");
        value.put("summary", link.summary());
        return value;
    }

    private Optional<String> recordTypeForColumn(String column) {
        String normalized = column == null ? "" : column.replace("_", "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "transactionid", "txnid" -> Optional.of("TRANSACTION");
            case "merchantid" -> Optional.of("MERCHANT");
            case "alertid" -> Optional.of("ALERT");
            case "caseid", "compliancecaseid" -> Optional.of("CASE");
            case "multiassetcustomerid" -> Optional.of("MULTI_ASSET_CUSTOMER");
            case "multiassettransactionid" -> Optional.of("MULTI_ASSET_TRANSACTION");
            case "multiassetrisksignalid" -> Optional.of("MULTI_ASSET_RISK_SIGNAL");
            case "mobilemoneytransactioncontextid" -> Optional.of("MOBILE_MONEY_TRANSACTION_CONTEXT");
            case "mobilemoneyriskprofileid" -> Optional.of("MOBILE_MONEY_RISK_PROFILE");
            case "mobilemoneynetworkedgeid" -> Optional.of("MOBILE_MONEY_NETWORK_EDGE");
            case "vaspdirectoryentryid", "originatorvaspdirectoryentryid", "beneficiaryvaspdirectoryentryid" -> Optional.of("VASP_DIRECTORY_ENTRY");
            case "cryptowalletprofileid" -> Optional.of("CRYPTO_WALLET_PROFILE");
            case "walletscreeningrecordid" -> Optional.of("WALLET_SCREENING_RECORD");
            case "vaspscreeningrecordid" -> Optional.of("VASP_SCREENING_RECORD");
            case "travelrulejurisdictionpolicyid" -> Optional.of("TRAVEL_RULE_JURISDICTION_POLICY");
            case "travelruletransferid" -> Optional.of("TRAVEL_RULE_TRANSFER");
            case "virtualassetregulatoraccessgrantid" -> Optional.of("VIRTUAL_ASSET_REGULATOR_ACCESS_GRANT");
            case "virtualassetregulatoraccesslogid" -> Optional.of("VIRTUAL_ASSET_REGULATOR_ACCESS_LOG");
            case "chargebackdisputeid", "disputeid" -> Optional.of("CHARGEBACK_DISPUTE");
            case "merchantdocumentid", "documentid" -> Optional.of("MERCHANT_DOCUMENT");
            case "regulatorysubmissionid", "submissionid" -> Optional.of("REGULATORY_SUBMISSION");
            case "sarid", "suspiciousactivityreportid" -> Optional.of("SUSPICIOUS_ACTIVITY_REPORT");
            case "ruledefinitionid" -> Optional.of("RULE_DEFINITION");
            case "velocityruleid" -> Optional.of("VELOCITY_RULE");
            case "riskthresholdid" -> Optional.of("RISK_THRESHOLD");
            case "marketsurveillancesignalid" -> Optional.of("MARKET_SURVEILLANCE_SIGNAL");
            case "marketorderid" -> Optional.of("MARKET_ORDER");
            case "marketexecutionid" -> Optional.of("MARKET_EXECUTION");
            case "corporateintelligencecheckid" -> Optional.of("CORPORATE_INTELLIGENCE_CHECK");
            case "fixmessageeventid" -> Optional.of("FIX_MESSAGE_EVENT");
            case "ruleversionid" -> Optional.of("RULE_VERSION");
            case "executionid", "reportexecutionid" -> Optional.of("REPORT_EXECUTION");
            case "scheduleid", "reportscheduleid" -> Optional.of("REPORT_SCHEDULE");
            case "userid", "preparedbyid", "reviewedbyid", "approvedbyid", "filedbyid" -> Optional.of("USER");
            case "pspid" -> Optional.of("PSP");
            case "reportid" -> Optional.of("REPORT");
            case "invoiceid" -> Optional.of("INVOICE");
            case "subscriptionid" -> Optional.of("SUBSCRIPTION");
            case "cbksubmissionid" -> Optional.of("CBK_SUBMISSION");
            case "auditlogid" -> Optional.of("AUDIT_LOG");
            case "roleid" -> Optional.of("ROLE");
            default -> Optional.empty();
        };
    }
}
