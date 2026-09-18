package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspTrustee;
import com.posgateway.aml.integration.cbk.records.TrusteeRecord;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspTrustee} entities to {@link TrusteeRecord} DTOs for CBK GDI endpoint #3.
 */
public final class PspTrusteeMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspTrusteeMapper() {}

    public static TrusteeRecord toRecord(PspTrustee entity, String institutionCode) {
        TrusteeRecord r = new TrusteeRecord();
        r.setPspId(institutionCode);
        r.setTrustCompName(entity.getTrustCompName());
        r.setDirectorsTrustComp(entity.getDirectorsTrustComp());
        r.setTrusteeNames(entity.getTrusteeNames());
        r.setTrusteeGender(entity.getTrusteeGender());
        r.setDob(entity.getDob() != null ? entity.getDob().format(DATE_FMT) : null);
        r.setNationality(entity.getNationality());
        r.setResidentCountry(entity.getResidentCountry());
        r.setIdNoPassport(entity.getIdNoPassport());
        r.setPin(entity.getPin());
        r.setContactNumber(entity.getContactNumber());
        r.setQualifications(entity.getQualifications());
        r.setOthersTrusteeships(entity.getOthersTrusteeships());
        r.setDisclosures(entity.getDisclosures());
        r.setShareholders(entity.getShareholders());
        r.setShareholdingPercentage(entity.getShareholdingPercentage() != null
                ? entity.getShareholdingPercentage().toPlainString() : null);
        return r;
    }

    public static List<TrusteeRecord> toRecords(List<PspTrustee> entities, String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
