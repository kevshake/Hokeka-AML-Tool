package com.posgateway.aml.service.cbk.projection;

/**
 * JPQL constructor-expression projection for SYSTEM_ACTIVITY aggregation.
 * One row per hour-of-day (0-23) in the reporting window.
 */
public class HourlyActivityAggRow {

    private final Integer hourOfDay;
    private final Long count;

    public HourlyActivityAggRow(Integer hourOfDay, Long count) {
        this.hourOfDay = hourOfDay;
        this.count = count;
    }

    public Integer getHourOfDay() { return hourOfDay; }
    public Long getCount() { return count; }
}
