package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.ReportScheduleDTO;
import com.posgateway.aml.dto.reporting.ReportScheduleRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.reporting.ReportScheduleRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Report Scheduling Service
 * Handles scheduled report configuration and management
 */
@Service
public class ReportSchedulingService {

    private static final Logger logger = LoggerFactory.getLogger(ReportSchedulingService.class);

    private final ReportScheduleRepository reportScheduleRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;

    public ReportSchedulingService(ReportScheduleRepository reportScheduleRepository,
                                   ReportRepository reportRepository,
                                   UserRepository userRepository,
                                   PspIsolationService pspIsolationService) {
        this.reportScheduleRepository = reportScheduleRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
    }

    /**
     * Schedule a new recurring report
     */
    @Transactional
    public ReportScheduleDTO scheduleReport(Long reportId,
                                            ReportScheduleRequest scheduleConfig,
                                            Map<String, Object> parameters,
                                            Long userId,
                                            Long pspId) {
        logger.info("Scheduling report: {}, user: {}, psp: {}", reportId, userId, pspId);
        
        // Find report
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        
        // Sanitize PSP ID
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        // Validate schedule configuration
        validateScheduleConfig(scheduleConfig);
        
        // Create schedule
        ReportSchedule schedule = new ReportSchedule();
        schedule.setReport(report);
        schedule.setPspId(effectivePspId);
        schedule.setScheduleName(scheduleConfig.getScheduleName());
        schedule.setFrequency(scheduleConfig.getFrequency());
        schedule.setCronExpression(scheduleConfig.getCronExpression());
        schedule.setTimeOfDay(scheduleConfig.getTimeOfDay() != null ? scheduleConfig.getTimeOfDay() : LocalTime.of(0, 0));
        schedule.setDayOfWeek(scheduleConfig.getDayOfWeek());
        schedule.setDayOfMonth(scheduleConfig.getDayOfMonth());
        schedule.setTimezone(scheduleConfig.getTimezone() != null ? scheduleConfig.getTimezone() : "UTC");
        schedule.setDefaultParameters(scheduleConfig.getDefaultParameters());
        schedule.setDefaultFilters(scheduleConfig.getDefaultFilters());
        schedule.setDateRangeType(scheduleConfig.getDateRangeType());
        schedule.setEmailRecipients(scheduleConfig.getEmailRecipients());
        schedule.setEmailSubject(scheduleConfig.getEmailSubject());
        schedule.setEmailBody(scheduleConfig.getEmailBody());
        schedule.setExportFormats(scheduleConfig.getExportFormats() != null ? scheduleConfig.getExportFormats() : List.of("PDF"));
        schedule.setIsActive(true);
        schedule.setRunCount(0);
        schedule.setFailCount(0);
        
        User user = userRepository.findById(userId).orElse(null);
        schedule.setCreatedBy(user);
        
        // Calculate next run time
        schedule.setNextRunAt(calculateNextRunTime(schedule));
        
        ReportSchedule saved = reportScheduleRepository.save(schedule);
        logger.info("Report scheduled successfully: {}", saved.getId());
        
        return convertToDTO(saved);
    }

    /**
     * Unschedule (deactivate) a report schedule
     */
    @Transactional
    public boolean unscheduleReport(Long scheduleId) {
        logger.info("Unscheduling report: {}", scheduleId);
        
        ReportSchedule schedule = reportScheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(schedule.getPspId());
        
        schedule.setIsActive(false);
        schedule.setNextRunAt(null);
        reportScheduleRepository.save(schedule);
        
        logger.info("Report unscheduled: {}", scheduleId);
        return true;
    }

    /**
     * Get scheduled reports for a PSP
     */
    @Transactional(readOnly = true)
    public Page<ReportScheduleDTO> getScheduledReports(Long pspId, int page, int size, String sortBy, String sortDirection) {
        logger.debug("Getting scheduled reports for psp: {}", pspId);
        
        // Sanitize PSP ID
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        Sort sort = Sort.by(sortDirection.equalsIgnoreCase("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC, 
                             sortBy != null ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ReportSchedule> schedules;
        if (effectivePspId != null) {
            schedules = reportScheduleRepository.findByPspId(effectivePspId, pageable);
        } else {
            schedules = reportScheduleRepository.findAll(pageable);
        }
        
        return schedules.map(this::convertToDTO);
    }

    /**
     * Update an existing schedule
     */
    @Transactional
    public ReportScheduleDTO updateSchedule(Long scheduleId, ReportScheduleRequest newConfig) {
        logger.info("Updating schedule: {}", scheduleId);
        
        ReportSchedule schedule = reportScheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(schedule.getPspId());
        
        // Validate new configuration
        validateScheduleConfig(newConfig);
        
        // Update fields
        if (newConfig.getScheduleName() != null) {
            schedule.setScheduleName(newConfig.getScheduleName());
        }
        if (newConfig.getFrequency() != null) {
            schedule.setFrequency(newConfig.getFrequency());
        }
        if (newConfig.getCronExpression() != null) {
            schedule.setCronExpression(newConfig.getCronExpression());
        }
        if (newConfig.getTimeOfDay() != null) {
            schedule.setTimeOfDay(newConfig.getTimeOfDay());
        }
        if (newConfig.getDayOfWeek() != null) {
            schedule.setDayOfWeek(newConfig.getDayOfWeek());
        }
        if (newConfig.getDayOfMonth() != null) {
            schedule.setDayOfMonth(newConfig.getDayOfMonth());
        }
        if (newConfig.getTimezone() != null) {
            schedule.setTimezone(newConfig.getTimezone());
        }
        if (newConfig.getDefaultParameters() != null) {
            schedule.setDefaultParameters(newConfig.getDefaultParameters());
        }
        if (newConfig.getDefaultFilters() != null) {
            schedule.setDefaultFilters(newConfig.getDefaultFilters());
        }
        if (newConfig.getDateRangeType() != null) {
            schedule.setDateRangeType(newConfig.getDateRangeType());
        }
        if (newConfig.getEmailRecipients() != null) {
            schedule.setEmailRecipients(newConfig.getEmailRecipients());
        }
        if (newConfig.getEmailSubject() != null) {
            schedule.setEmailSubject(newConfig.getEmailSubject());
        }
        if (newConfig.getEmailBody() != null) {
            schedule.setEmailBody(newConfig.getEmailBody());
        }
        if (newConfig.getExportFormats() != null) {
            schedule.setExportFormats(newConfig.getExportFormats());
        }
        
        // Recalculate next run time
        schedule.setNextRunAt(calculateNextRunTime(schedule));
        
        ReportSchedule saved = reportScheduleRepository.save(schedule);
        logger.info("Schedule updated: {}", scheduleId);
        
        return convertToDTO(saved);
    }

    /**
     * Get a specific schedule by ID
     */
    @Transactional(readOnly = true)
    public ReportScheduleDTO getScheduleById(Long scheduleId) {
        ReportSchedule schedule = reportScheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(schedule.getPspId());
        
        return convertToDTO(schedule);
    }

    /**
     * Activate a schedule
     */
    @Transactional
    public ReportScheduleDTO activateSchedule(Long scheduleId) {
        logger.info("Activating schedule: {}", scheduleId);
        
        ReportSchedule schedule = reportScheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
        
        pspIsolationService.validatePspAccess(schedule.getPspId());
        
        schedule.setIsActive(true);
        schedule.setNextRunAt(calculateNextRunTime(schedule));
        
        ReportSchedule saved = reportScheduleRepository.save(schedule);
        return convertToDTO(saved);
    }

    /**
     * Delete a schedule permanently
     */
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        logger.info("Deleting schedule: {}", scheduleId);
        
        ReportSchedule schedule = reportScheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + scheduleId));
        
        pspIsolationService.validatePspAccess(schedule.getPspId());
        
        reportScheduleRepository.delete(schedule);
        logger.info("Schedule deleted: {}", scheduleId);
    }

    /**
     * Calculate the next run time for a schedule
     */
    protected LocalDateTime calculateNextRunTime(ReportSchedule schedule) {
        if (!Boolean.TRUE.equals(schedule.getIsActive())) {
            return null;
        }
        
        ZoneId zoneId = ZoneId.of(schedule.getTimezone() != null ? schedule.getTimezone() : "UTC");
        LocalDateTime now = LocalDateTime.now(zoneId);
        LocalTime timeOfDay = schedule.getTimeOfDay() != null ? schedule.getTimeOfDay() : LocalTime.MIDNIGHT;
        
        // If custom cron expression provided, use it
        if (schedule.getCronExpression() != null && !schedule.getCronExpression().isEmpty()) {
            try {
                CronExpression cron = CronExpression.parse(schedule.getCronExpression());
                return cron.next(LocalDateTime.now(zoneId));
            } catch (Exception e) {
                logger.warn("Invalid cron expression: {}", schedule.getCronExpression());
            }
        }
        
        switch (schedule.getFrequency()) {
            case DAILY:
                LocalDateTime nextDaily = now.with(timeOfDay);
                if (!nextDaily.isAfter(now)) {
                    nextDaily = nextDaily.plusDays(1);
                }
                return nextDaily;
                
            case WEEKLY:
                int dayOfWeek = schedule.getDayOfWeek() != null ? schedule.getDayOfWeek() : 1; // Monday default
                LocalDateTime nextWeekly = now.with(timeOfDay);
                int currentDayOfWeek = nextWeekly.getDayOfWeek().getValue();
                int daysToAdd = (dayOfWeek - currentDayOfWeek + 7) % 7;
                if (daysToAdd == 0 && !nextWeekly.isAfter(now)) {
                    daysToAdd = 7;
                }
                return nextWeekly.plusDays(daysToAdd);
                
            case MONTHLY:
                int dayOfMonth = schedule.getDayOfMonth() != null ? schedule.getDayOfMonth() : 1; // 1st default
                LocalDateTime nextMonthly = now.withDayOfMonth(Math.min(dayOfMonth, now.toLocalDate().lengthOfMonth()))
                                              .with(timeOfDay);
                if (!nextMonthly.isAfter(now)) {
                    nextMonthly = nextMonthly.plusMonths(1).withDayOfMonth(Math.min(dayOfMonth, 
                        nextMonthly.toLocalDate().plusMonths(1).lengthOfMonth()));
                }
                return nextMonthly;
                
            case QUARTERLY:
                LocalDateTime nextQuarterly = now.with(timeOfDay);
                int currentMonth = nextQuarterly.getMonthValue();
                int nextQuarterMonth = ((currentMonth - 1) / 3 + 1) * 3 + 1;
                if (nextQuarterMonth > 12) {
                    nextQuarterly = nextQuarterly.plusYears(1).withMonth(1);
                } else {
                    nextQuarterly = nextQuarterly.withMonth(nextQuarterMonth);
                }
                if (!nextQuarterly.isAfter(now)) {
                    nextQuarterly = nextQuarterly.plusMonths(3);
                }
                return nextQuarterly;
                
            case YEARLY:
                LocalDateTime nextYearly = now.withMonth(1).withDayOfMonth(1).with(timeOfDay);
                if (!nextYearly.isAfter(now)) {
                    nextYearly = nextYearly.plusYears(1);
                }
                return nextYearly;
                
            default:
                return null;
        }
    }

    /**
     * Validate schedule configuration
     */
    private void validateScheduleConfig(ReportScheduleRequest config) {
        if (config.getScheduleName() == null || config.getScheduleName().trim().isEmpty()) {
            throw new IllegalArgumentException("Schedule name is required");
        }
        
        if (config.getFrequency() == null) {
            throw new IllegalArgumentException("Frequency is required");
        }
        
        // Validate cron expression if provided
        if (config.getCronExpression() != null && !config.getCronExpression().isEmpty()) {
            try {
                CronExpression.parse(config.getCronExpression());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid cron expression: " + e.getMessage());
            }
        }
        
        // Validate email recipients if provided
        if (config.getEmailRecipients() != null) {
            for (String email : config.getEmailRecipients()) {
                if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    throw new IllegalArgumentException("Invalid email address: " + email);
                }
            }
        }
    }

    /**
     * Convert entity to DTO
     */
    private ReportScheduleDTO convertToDTO(ReportSchedule schedule) {
        ReportScheduleDTO dto = new ReportScheduleDTO();
        dto.setId(schedule.getId());
        dto.setReportId(schedule.getReport() != null ? schedule.getReport().getId() : null);
        dto.setReportName(schedule.getReport() != null ? schedule.getReport().getReportName() : null);
        dto.setReportCode(schedule.getReport() != null ? schedule.getReport().getReportCode() : null);
        dto.setPspId(schedule.getPspId());
        dto.setScheduleName(schedule.getScheduleName());
        dto.setFrequency(schedule.getFrequency());
        dto.setCronExpression(schedule.getCronExpression());
        dto.setTimeOfDay(schedule.getTimeOfDay());
        dto.setDayOfWeek(schedule.getDayOfWeek());
        dto.setDayOfMonth(schedule.getDayOfMonth());
        dto.setTimezone(schedule.getTimezone());
        dto.setDefaultParameters(schedule.getDefaultParameters());
        dto.setDefaultFilters(schedule.getDefaultFilters());
        dto.setDateRangeType(schedule.getDateRangeType());
        dto.setEmailRecipients(schedule.getEmailRecipients());
        dto.setEmailSubject(schedule.getEmailSubject());
        dto.setEmailBody(schedule.getEmailBody());
        dto.setExportFormats(schedule.getExportFormats());
        dto.setIsActive(schedule.getIsActive());
        dto.setLastRunAt(schedule.getLastRunAt());
        dto.setNextRunAt(schedule.getNextRunAt());
        dto.setRunCount(schedule.getRunCount());
        dto.setFailCount(schedule.getFailCount());
        
        if (schedule.getCreatedBy() != null) {
            dto.setCreatedBy(schedule.getCreatedBy().getId());
            dto.setCreatedByName(schedule.getCreatedBy().getFullName());
        }
        
        dto.setCreatedAt(schedule.getCreatedAt());
        dto.setUpdatedAt(schedule.getUpdatedAt());
        
        return dto;
    }
}
