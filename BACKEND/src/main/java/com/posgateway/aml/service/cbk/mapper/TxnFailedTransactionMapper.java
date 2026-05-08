package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.integration.cbk.records.FailedTransactionRecord;
import com.posgateway.aml.service.cbk.projection.FailedTransactionAggRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link FailedTransactionAggRow} aggregation results to
 * {@link FailedTransactionRecord} DTOs for CBK endpoint #17
 * (FAILED_TRANSACTIONS, daily — previous day DECLINED / MANUAL_REVIEW).
 *
 * <p>Reason text comes from acquirerResponse (truncated to 100 chars).
 * When the DB row has a null acquirerResponse, the query returns null and
 * the FailedTransactionAggRow already carries null; the mapper substitutes "TRRC99".
 */
public final class TxnFailedTransactionMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int REASON_MAX_LEN = 100;
    private static final String FALLBACK_REASON = "TRRC99";

    private TxnFailedTransactionMapper() {}

    public static List<FailedTransactionRecord> toRecords(List<FailedTransactionAggRow> rows,
                                                           String institutionCode) {
        String reportingDate = LocalDate.now().minusDays(1).format(DATE_FMT);
        return rows.stream()
                .map(r -> {
                    FailedTransactionRecord rec = new FailedTransactionRecord();
                    rec.setBankId(institutionCode);
                    rec.setReportingDate(reportingDate);
                    rec.setMerchantId(r.getMerchantId());
                    rec.setRejectionFailureReason(resolveReason(r.getReason()));
                    rec.setNumberOfTransactions(String.valueOf(r.getCount()));
                    rec.setValueOfTransactions(centsToCurrencyString(r.getValueCents()));
                    // Fields not available from aggregation — left null per contract
                    rec.setCustomerAccountNumber(null);
                    rec.setChannelOfSettlement(null);
                    rec.setEmail(null);
                    return rec;
                })
                .collect(Collectors.toList());
    }

    private static String resolveReason(String raw) {
        if (raw == null || raw.isBlank()) return FALLBACK_REASON;
        return raw.length() <= REASON_MAX_LEN ? raw : raw.substring(0, REASON_MAX_LEN);
    }

    private static String centsToCurrencyString(Long cents) {
        if (cents == null) return "0.00";
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
