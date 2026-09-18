package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.integration.cbk.records.TransactionDetailRecord;
import com.posgateway.aml.service.cbk.projection.TransactionDetailAggRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Maps {@link TransactionDetailAggRow} aggregation results to
 * {@link TransactionDetailRecord} DTOs for CBK endpoint #14
 * (TRANSACTION_DETAILS, monthly).
 */
public final class TxnTransactionDetailMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private TxnTransactionDetailMapper() {}

    public static List<TransactionDetailRecord> toRecords(List<TransactionDetailAggRow> rows,
                                                           String institutionCode) {
        String reportingDate = LocalDate.now().withDayOfMonth(1).minusMonths(1).format(DATE_FMT);
        AtomicInteger rowNum = new AtomicInteger(1);
        return rows.stream()
                .map(r -> {
                    TransactionDetailRecord rec = new TransactionDetailRecord();
                    rec.setRowId(String.valueOf(rowNum.getAndIncrement()));
                    rec.setReportingDate(reportingDate);
                    rec.setCardBrandType(r.getCardBrand());
                    rec.setCardType(r.getCardType());
                    rec.setCardClassType(r.getCardClass());
                    rec.setChannelType(r.getChannelType());
                    // Mobile money / mobile banking partners — not derived from transaction data
                    rec.setMobileMonePartnerId(null);
                    rec.setMobileBankingPartnerId(null);
                    rec.setTransactionCategoryType(null);
                    rec.setTotalNumberOfTransactionsDone(String.valueOf(r.getCount()));
                    rec.setTotalValueOfTransactionsDone(centsToCurrencyString(r.getValueCents()));
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
