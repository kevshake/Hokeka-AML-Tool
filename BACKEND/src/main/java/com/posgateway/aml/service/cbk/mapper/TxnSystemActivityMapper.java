package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.integration.cbk.records.SystemActivityRecord;
import com.posgateway.aml.service.cbk.projection.HourlyActivityAggRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps {@link HourlyActivityAggRow} aggregation results to exactly 24
 * {@link SystemActivityRecord} DTOs (one per hour 0-23) for CBK endpoint #9
 * (SYSTEM_ACTIVITY, daily).
 *
 * <p>Hours with no transactions are padded with count=0 and TPS=0.00.
 * TPS is derived as count/3600 (transactions per second averaged over the hour).
 */
public final class TxnSystemActivityMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int SECONDS_PER_HOUR = 3600;

    private TxnSystemActivityMapper() {}

    public static List<SystemActivityRecord> toRecords(List<HourlyActivityAggRow> rows,
                                                        String institutionCode) {
        String reportingDate = LocalDate.now().minusDays(1).format(DATE_FMT);
        String pspIdStr = institutionCode;

        // Build a map from hourOfDay -> count for quick lookup
        Map<Integer, Long> hourCountMap = rows.stream()
                .filter(r -> r.getHourOfDay() != null)
                .collect(Collectors.toMap(HourlyActivityAggRow::getHourOfDay,
                                          HourlyActivityAggRow::getCount,
                                          (a, b) -> a + b));

        List<SystemActivityRecord> result = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            long count = hourCountMap.getOrDefault(hour, 0L);
            SystemActivityRecord rec = new SystemActivityRecord();
            rec.setPspId(pspIdStr);
            rec.setReportingDate(reportingDate);
            rec.setHourOfTheDay(String.format("%02d", hour));
            rec.setNumberOfTransactionsPerHour(String.valueOf(count));
            rec.setNumberOfTxnsPerSec(tpsString(count));
            result.add(rec);
        }
        return result;
    }

    /** Average TPS = count / 3600, formatted to 4 decimal places. */
    private static String tpsString(long count) {
        if (count == 0L) return "0.0000";
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(SECONDS_PER_HOUR), 4, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
