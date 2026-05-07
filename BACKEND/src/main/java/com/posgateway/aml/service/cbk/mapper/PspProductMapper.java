package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspProduct;
import com.posgateway.aml.integration.cbk.records.ProductRecord;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspProduct} entities to {@link ProductRecord} DTOs for CBK GDI endpoint #10.
 *
 * <p>The record expects a REPORTING_DATE. The entity has no stored reporting date —
 * the first day of the previous month is used, matching the monthly cadence.
 */
public final class PspProductMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspProductMapper() {}

    public static ProductRecord toRecord(PspProduct entity, String institutionCode) {
        ProductRecord r = new ProductRecord();
        r.setPspId(institutionCode);
        // Reporting date: first day of the previous month (matches monthly CBK cadence)
        r.setReportingDate(LocalDate.now().withDayOfMonth(1).minusMonths(1).format(DATE_FMT));
        r.setProductOwnershipFlag(entity.getProductOwnershipFlag());
        r.setProductOwnershipCategory(entity.getProductOwnershipCategory());
        r.setProductPartnerName(entity.getProductPartnerName());
        r.setProductTransactionCode(entity.getProductTransactionCode());
        // entity field is genderSegment; record field is GENDER
        r.setGender(entity.getGenderSegment());
        r.setStatusCode(entity.getStatusCode());
        r.setBandCode(entity.getBandCode());
        r.setNoOfCustomers(entity.getNoOfCustomers() != null
                ? entity.getNoOfCustomers().toString() : null);
        r.setNoOfTransactions(entity.getNoOfTransactions() != null
                ? entity.getNoOfTransactions().toString() : null);
        r.setValueOfTransactions(entity.getValueOfTransactions() != null
                ? entity.getValueOfTransactions().toPlainString() : null);
        r.setProductName(entity.getProductName());
        return r;
    }

    public static List<ProductRecord> toRecords(List<PspProduct> entities, String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
