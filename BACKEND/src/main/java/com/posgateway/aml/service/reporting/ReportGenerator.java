package com.posgateway.aml.service.reporting;

import java.time.LocalDate;
import java.util.Map;

/**
 * Report Generator Interface
 * Strategy interface for different report types
 */
public interface ReportGenerator {

    /**
     * Generate report data
     * 
     * @param pspId     PSP ID to filter data
     * @param startDate Start date
     * @param endDate   End date
     * @return Map representing report data (headers -> rows/content)
     */
    Map<String, Object> generate(Long pspId, LocalDate startDate, LocalDate endDate);

    /**
     * Get Report Type
     */
    String getType();
}
