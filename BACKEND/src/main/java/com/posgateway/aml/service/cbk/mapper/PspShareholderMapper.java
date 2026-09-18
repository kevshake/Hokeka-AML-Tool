package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspShareholder;
import com.posgateway.aml.integration.cbk.records.ShareholderRecord;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspShareholder} entities to {@link ShareholderRecord} DTOs for CBK GDI endpoint #4.
 */
public final class PspShareholderMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspShareholderMapper() {}

    public static ShareholderRecord toRecord(PspShareholder entity, String institutionCode) {
        ShareholderRecord r = new ShareholderRecord();
        r.setPspId(institutionCode);
        r.setShareholderName(entity.getShareholderName());
        r.setShareholderGender(entity.getShareholderGender());
        r.setShareholderType(entity.getShareholderType());
        r.setDobOrRegDate(entity.getDobOrRegDate() != null
                ? entity.getDobOrRegDate().format(DATE_FMT) : null);
        r.setNationality(entity.getNationality());
        r.setResidentCountry(entity.getResidentCountry());
        r.setCountryOfInc(entity.getCountryOfInc());
        r.setIdNoPassport(entity.getIdNoPassport());
        r.setPin(entity.getPin());
        r.setContactNumber(entity.getContactNumber());
        r.setQualifications(entity.getQualifications());
        r.setPreviousEmployment(entity.getPreviousEmployment());
        r.setOnboardingDate(entity.getOnboardingDate() != null
                ? entity.getOnboardingDate().format(DATE_FMT) : null);
        r.setNoOfSharesHeld(entity.getNoOfSharesHeld() != null
                ? entity.getNoOfSharesHeld().toString() : null);
        r.setShareValue(entity.getShareValue() != null
                ? entity.getShareValue().toPlainString() : null);
        r.setPercentageOfShare(entity.getPercentageOfShare() != null
                ? entity.getPercentageOfShare().toPlainString() : null);
        return r;
    }

    public static List<ShareholderRecord> toRecords(List<PspShareholder> entities, String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
