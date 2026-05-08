package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.integration.cbk.records.MerchantTransactionRecord;
import com.posgateway.aml.service.cbk.projection.MerchantSettlementAggRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link MerchantSettlementAggRow} aggregation results to
 * {@link MerchantTransactionRecord} DTOs for CBK endpoint #16
 * (MERCHANT_TRANSACTIONS, daily — previous day approved transactions).
 */
public final class TxnMerchantTransactionMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private TxnMerchantTransactionMapper() {}

    public static List<MerchantTransactionRecord> toRecords(List<MerchantSettlementAggRow> rows,
                                                             String institutionCode) {
        String reportingDate = LocalDate.now().minusDays(1).format(DATE_FMT);
        return rows.stream()
                .map(r -> {
                    MerchantTransactionRecord rec = new MerchantTransactionRecord();
                    rec.setBankId(institutionCode);
                    rec.setReportingDate(reportingDate);
                    rec.setMerchantId(r.getMerchantId());
                    rec.setEmailAddress(r.getContactEmail());
                    rec.setMerchantCountry(r.getMerchantCountry());
                    rec.setEconomicSectors(r.getMcc());
                    rec.setNumberOfTransactions(String.valueOf(r.getCount()));
                    rec.setValueOfTransactions(centsToCurrencyString(r.getValueCents()));
                    // Fields not derivable from transaction aggregation — left null per contract
                    rec.setMerchantAccountNumber(null);
                    rec.setChannelOfSettlement(null);
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
