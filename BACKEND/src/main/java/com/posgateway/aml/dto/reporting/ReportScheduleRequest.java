package com.posgateway.aml.dto.reporting;

import com.posgateway.aml.entity.reporting.DateRangeType;
import com.posgateway.aml.entity.reporting.ScheduleFrequency;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for Report Schedule requests
 */
public class ReportScheduleRequest {
    private Long reportId;
    private String scheduleName;
    private ScheduleFrequency frequency;
    private String cronExpression;
    private LocalTime timeOfDay;
    private Integer dayOfWeek;
    private Integer dayOfMonth;
    private String timezone;
    private Map<String, Object> defaultParameters;
    private Map<String, Object> defaultFilters;
    private DateRangeType dateRangeType;
    private List<String> emailRecipients;
    private String emailSubject;
    private String emailBody;
    private List<String> exportFormats;
    private Long pspId;

    // Getters and Setters
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }

    public String getScheduleName() { return scheduleName; }
    public void setScheduleName(String scheduleName) { this.scheduleName = scheduleName; }

    public ScheduleFrequency getFrequency() { return frequency; }
    public void setFrequency(ScheduleFrequency frequency) { this.frequency = frequency; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    public LocalTime getTimeOfDay() { return timeOfDay; }
    public void setTimeOfDay(LocalTime timeOfDay) { this.timeOfDay = timeOfDay; }

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public Map<String, Object> getDefaultParameters() { return defaultParameters; }
    public void setDefaultParameters(Map<String, Object> defaultParameters) { this.defaultParameters = defaultParameters; }

    public Map<String, Object> getDefaultFilters() { return defaultFilters; }
    public void setDefaultFilters(Map<String, Object> defaultFilters) { this.defaultFilters = defaultFilters; }

    public DateRangeType getDateRangeType() { return dateRangeType; }
    public void setDateRangeType(DateRangeType dateRangeType) { this.dateRangeType = dateRangeType; }

    public List<String> getEmailRecipients() { return emailRecipients; }
    public void setEmailRecipients(List<String> emailRecipients) { this.emailRecipients = emailRecipients; }

    public String getEmailSubject() { return emailSubject; }
    public void setEmailSubject(String emailSubject) { this.emailSubject = emailSubject; }

    public String getEmailBody() { return emailBody; }
    public void setEmailBody(String emailBody) { this.emailBody = emailBody; }

    public List<String> getExportFormats() { return exportFormats; }
    public void setExportFormats(List<String> exportFormats) { this.exportFormats = exportFormats; }

    public Long getPspId() { return pspId; }
    public void setPspId(Long pspId) { this.pspId = pspId; }
}
