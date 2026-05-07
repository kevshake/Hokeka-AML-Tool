package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspTariffTemplate;
import com.posgateway.aml.integration.cbk.records.TransactionTariffRecord;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Maps {@link PspTariffTemplate} entities to {@link TransactionTariffRecord} DTOs
 * for CBK GDI endpoint #15.
 *
 * <p>The record has a {@code ROW ID} field (space in key) with no direct entity counterpart —
 * a 1-based sequential index is used per the CBK spec convention.
 * The {@code REPORTING DATE} is set to the first day of the previous month.
 */
public final class PspTariffTemplateMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspTariffTemplateMapper() {}

    public static TransactionTariffRecord toRecord(PspTariffTemplate entity, String institutionCode,
                                                    int rowIndex) {
        TransactionTariffRecord r = new TransactionTariffRecord();
        r.setRowId(String.valueOf(rowIndex));
        r.setReportingDate(LocalDate.now().withDayOfMonth(1).minusMonths(1).format(DATE_FMT));
        r.setChannelUsed(entity.getChannelUsed());
        r.setChannelPartnerName(entity.getChannelPartnerName());
        r.setChargeDescription(entity.getChargeDescription());
        r.setPercentageTransactionCost(entity.getPercentageTransactionCost() != null
                ? entity.getPercentageTransactionCost().toPlainString() : null);
        r.setAbsoluteTransactionCost(entity.getAbsoluteTransactionCost() != null
                ? entity.getAbsoluteTransactionCost().toPlainString() : null);
        return r;
    }

    public static List<TransactionTariffRecord> toRecords(List<PspTariffTemplate> entities,
                                                           String institutionCode) {
        AtomicInteger idx = new AtomicInteger(1);
        return entities.stream()
                .map(e -> toRecord(e, institutionCode, idx.getAndIncrement()))
                .collect(Collectors.toList());
    }
}
