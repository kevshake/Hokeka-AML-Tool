package com.posgateway.aml.service.case_management;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Business Day Calculator
 * Calculates business days excluding weekends and holidays
 */
@Component
public class BusinessDayCalculator {

    private static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    @Value("${business.holidays:}")
    private String holidayDatesString; // Format: "2024-01-01,2024-12-25"

    private Set<String> holidayDates = new HashSet<>();

    /**
     * Add business days to a date
     */
    public LocalDateTime addBusinessDays(LocalDateTime startDate, int businessDays) {
        LocalDateTime current = startDate;
        int daysAdded = 0;

        while (daysAdded < businessDays) {
            current = current.plusDays(1);
            if (isBusinessDay(current)) {
                daysAdded++;
            }
        }

        return current;
    }

    /**
     * Check if a date is a business day
     */
    public boolean isBusinessDay(LocalDateTime date) {
        // Check weekend
        if (WEEKEND.contains(date.getDayOfWeek())) {
            return false;
        }

        // Check holidays
        loadHolidays();
        String dateStr = date.toLocalDate().toString();
        return !holidayDates.contains(dateStr);
    }

    /**
     * Count business days between two dates
     */
    public long countBusinessDays(LocalDateTime start, LocalDateTime end) {
        long count = 0;
        LocalDateTime current = start;

        while (!current.isAfter(end)) {
            if (isBusinessDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }

        return count;
    }

    /**
     * Load holidays from configuration
     */
    private void loadHolidays() {
        if (holidayDates.isEmpty() && holidayDatesString != null && !holidayDatesString.isEmpty()) {
            holidayDates.addAll(Arrays.asList(holidayDatesString.split(",")));
        }
    }
}

