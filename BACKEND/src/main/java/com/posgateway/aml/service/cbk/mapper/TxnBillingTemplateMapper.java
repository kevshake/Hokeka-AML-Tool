package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.integration.cbk.records.BillingTemplateRecord;
import com.posgateway.aml.service.cbk.projection.BillingClassificationAggRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Maps {@link BillingClassificationAggRow} aggregation results to
 * {@link BillingTemplateRecord} DTOs for CBK endpoint #13
 * (BILLING_TEMPLATE, daily).
 */
public final class TxnBillingTemplateMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private TxnBillingTemplateMapper() {}

    public static List<BillingTemplateRecord> toRecords(List<BillingClassificationAggRow> rows,
                                                         String institutionCode) {
        String reportingDate = LocalDate.now().minusDays(1).format(DATE_FMT);
        AtomicInteger rowNum = new AtomicInteger(1);
        return rows.stream()
                .map(r -> {
                    BillingTemplateRecord rec = new BillingTemplateRecord();
                    rec.setRowId(String.valueOf(rowNum.getAndIncrement()));
                    rec.setReportingDate(reportingDate);
                    rec.setBillClassificationCode(r.getBillClassificationCode());
                    rec.setNumberOfTransaction(String.valueOf(r.getCount()));
                    rec.setValueOfTransactions(centsToCurrencyString(r.getValueCents()));
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
