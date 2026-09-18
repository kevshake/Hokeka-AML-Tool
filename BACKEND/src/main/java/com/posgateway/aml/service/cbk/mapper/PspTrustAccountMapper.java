package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspTrustAccount;
import com.posgateway.aml.integration.cbk.records.TrustAccountRecord;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspTrustAccount} entities to {@link TrustAccountRecord} DTOs
 * for CBK GDI endpoint #11.
 *
 * <p>Field name notes:
 * <ul>
 *   <li>Entity {@code openingBalance}  → record {@code TRUST_ACC_OPENING_BALANCE}</li>
 *   <li>Entity {@code interestEarned}  → record {@code TRUST_ACC_INTEREST_EARNED}</li>
 *   <li>Entity {@code interestUtilized} → record {@code TRUST_ACC_INTEREST_UTILIZED}</li>
 * </ul>
 */
public final class PspTrustAccountMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspTrustAccountMapper() {}

    public static TrustAccountRecord toRecord(PspTrustAccount entity, String institutionCode) {
        TrustAccountRecord r = new TrustAccountRecord();
        r.setPspId(institutionCode);
        r.setReportingDate(entity.getAsOfDate() != null
                ? entity.getAsOfDate().format(DATE_FMT) : null);
        r.setBankId(entity.getBankId());
        r.setBankAccountNumber(entity.getBankAccountNumber());
        r.setTrustAccDrTypeCode(entity.getTrustAccDrTypeCode());
        r.setOrgReceivingDonation(entity.getOrgReceivingDonation());
        r.setSectorCode(entity.getSectorCode());
        r.setTrustAccIntUtilizedDetails(entity.getTrustAccIntUtilizedDetails());
        r.setTrustAccOpeningBalance(entity.getOpeningBalance() != null
                ? entity.getOpeningBalance().toPlainString() : null);
        r.setPrincipalAmount(entity.getPrincipalAmount() != null
                ? entity.getPrincipalAmount().toPlainString() : null);
        r.setTrustAccInterestEarned(entity.getInterestEarned() != null
                ? entity.getInterestEarned().toPlainString() : null);
        r.setClosingBalance(entity.getClosingBalance() != null
                ? entity.getClosingBalance().toPlainString() : null);
        r.setTrustAccInterestUtilized(entity.getInterestUtilized() != null
                ? entity.getInterestUtilized().toPlainString() : null);
        r.setTrustFields(entity.getTrustFields());
        return r;
    }

    public static List<TrustAccountRecord> toRecords(List<PspTrustAccount> entities, String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
