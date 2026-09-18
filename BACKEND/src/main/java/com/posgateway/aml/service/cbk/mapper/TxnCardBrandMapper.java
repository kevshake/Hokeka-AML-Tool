package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.integration.cbk.records.CardBrandRecord;
import com.posgateway.aml.service.cbk.projection.CardBrandAggRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Maps {@link CardBrandAggRow} aggregation results to {@link CardBrandRecord}
 * DTOs for CBK endpoint #12 (CARD_BRANDS, monthly).
 *
 * <p>REPORTING_DATE is set to the first day of the previous month (start of the
 * window that was queried).
 */
public final class TxnCardBrandMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private TxnCardBrandMapper() {}

    public static List<CardBrandRecord> toRecords(List<CardBrandAggRow> rows,
                                                   String institutionCode) {
        String reportingDate = LocalDate.now().withDayOfMonth(1).minusMonths(1).format(DATE_FMT);
        AtomicInteger rowNum = new AtomicInteger(1);
        return rows.stream()
                .map(r -> {
                    CardBrandRecord rec = new CardBrandRecord();
                    rec.setRowId(String.valueOf(rowNum.getAndIncrement()));
                    rec.setReportingDate(reportingDate);
                    rec.setBankId(institutionCode);
                    rec.setCardBrandType(r.getCardBrand());
                    rec.setNumberOfTxns(String.valueOf(r.getCount()));
                    rec.setValueOfTxns(centsToCurrencyString(r.getValueCents()));
                    return rec;
                })
                .collect(Collectors.toList());
    }

    private static String centsToCurrencyString(Long cents) {
        if (cents == null) return "0.00";
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
