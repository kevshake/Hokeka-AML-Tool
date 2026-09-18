package com.posgateway.aml.service.reporting;

import com.posgateway.aml.entity.reporting.DateRangeType;
import com.posgateway.aml.entity.reporting.ExecutionStatus;
import com.posgateway.aml.service.reporting.ReportSchedulingService.ScheduledReportClaim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "reporting.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledReportRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReportRunner.class);

    private final ReportSchedulingService schedulingService;
    private final ReportGenerationService generationService;
    private final ReportDeliveryService deliveryService;

    public ScheduledReportRunner(ReportSchedulingService schedulingService,
                                 ReportGenerationService generationService,
                                 ReportDeliveryService deliveryService) {
        this.schedulingService = schedulingService;
        this.generationService = generationService;
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${reporting.scheduler.poll-ms:60000}",
               initialDelayString = "${reporting.scheduler.initial-delay-ms:30000}")
    public void dispatchDueReports() {
        for (ScheduledReportClaim claim : schedulingService.claimDueSchedules(LocalDateTime.now())) {
            for (String format : claim.exportFormats()) dispatch(claim, format);
        }
    }

    private void dispatch(ScheduledReportClaim claim, String format) {
        try {
            Map<String, Object> parameters = new LinkedHashMap<>(claim.parameters());
            if (!claim.filters().isEmpty()) parameters.put("filters", claim.filters());
            parameters.put("outputFormat", format);
            applyDateRange(parameters, claim.dateRangeType(), claim.timezone());

            String executionId = generationService.generateExecutionId();
            var queued = generationService.queueScheduledReport(executionId, claim.reportCode(), parameters,
                    claim.createdBy(), claim.pspId());
            Long historyId = schedulingService.recordScheduledDispatch(claim.scheduleId(), queued.getId(), claim.pspId(),
                    claim.scheduledFor(), format, claim.emailRecipients());
            generationService.generateScheduledReport(executionId, claim.reportCode(), parameters,
                    claim.createdBy(), claim.pspId()).whenComplete((result, error) -> {
                if (error != null || result == null || result.getStatus() == ExecutionStatus.FAILED) {
                    schedulingService.recordScheduledExecutionFailure(claim.scheduleId());
                    schedulingService.completeScheduledDispatch(historyId, "FAILED",
                            error != null ? error.getMessage() : result != null ? result.getErrorMessage() : "No result");
                    return;
                }
                try {
                    deliveryService.deliver(claim.emailRecipients(), claim.emailSubject(), claim.emailBody(),
                            generationService.reportFilePath(result.getId()));
                    schedulingService.completeScheduledDispatch(historyId,
                            claim.emailRecipients().isEmpty() ? "COMPLETED" : "DELIVERED", null);
                } catch (RuntimeException deliveryError) {
                    schedulingService.recordScheduledExecutionFailure(claim.scheduleId());
                    schedulingService.completeScheduledDispatch(historyId, "DELIVERY_FAILED", deliveryError.getMessage());
                }
            });
        } catch (RuntimeException e) {
            schedulingService.recordScheduledExecutionFailure(claim.scheduleId());
            log.error("Unable to dispatch scheduled report {} for PSP {}",
                    claim.scheduleId(), claim.pspId(), e);
        }
    }

    private void applyDateRange(Map<String, Object> parameters, DateRangeType rangeType, String timezone) {
        if (rangeType == null || rangeType == DateRangeType.CUSTOM) return;
        ZoneId zone = ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone);
        LocalDate today = ZonedDateTime.now(zone).toLocalDate();
        LocalDate start;
        LocalDate end;
        switch (rangeType) {
            case PREVIOUS_DAY -> { start = today.minusDays(1); end = start; }
            case PREVIOUS_WEEK -> {
                LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                start = thisMonday.minusWeeks(1); end = thisMonday.minusDays(1);
            }
            case PREVIOUS_MONTH -> {
                start = today.withDayOfMonth(1).minusMonths(1); end = start.with(TemporalAdjusters.lastDayOfMonth());
            }
            case PREVIOUS_QUARTER -> {
                int currentQuarterStart = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate thisQuarter = LocalDate.of(today.getYear(), currentQuarterStart, 1);
                start = thisQuarter.minusMonths(3); end = thisQuarter.minusDays(1);
            }
            case PREVIOUS_YEAR -> { start = LocalDate.of(today.getYear() - 1, 1, 1); end = LocalDate.of(today.getYear() - 1, 12, 31); }
            default -> { return; }
        }
        parameters.put("dateFrom", start.atStartOfDay());
        parameters.put("dateTo", end.plusDays(1).atStartOfDay().minusNanos(1));
    }
}
