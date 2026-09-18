package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspDirector;
import com.posgateway.aml.integration.cbk.records.DirectorRecord;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspDirector} entities to {@link DirectorRecord} DTOs for CBK GDI endpoint #2.
 */
public final class PspDirectorMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspDirectorMapper() {}

    public static DirectorRecord toRecord(PspDirector entity, String institutionCode) {
        DirectorRecord r = new DirectorRecord();
        r.setPspId(institutionCode);
        r.setDirectorNames(entity.getDirectorNames());
        r.setDirectorGender(entity.getDirectorGender());
        r.setTypeOfDirector(entity.getTypeOfDirector());
        r.setDob(entity.getDob() != null ? entity.getDob().format(DATE_FMT) : null);
        r.setNationality(entity.getNationality());
        r.setResidentCountry(entity.getResidentCountry());
        r.setIdNoPassport(entity.getIdNoPassport());
        r.setPin(entity.getPin());
        r.setContactNumber(entity.getContactNumber());
        r.setQualifications(entity.getQualifications());
        r.setOtherDirectorships(entity.getOtherDirectorships());
        r.setDateOfAppointment(entity.getDateOfAppointment() != null
                ? entity.getDateOfAppointment().format(DATE_FMT) : null);
        r.setDateOfRetirement(entity.getDateOfRetirement() != null
                ? entity.getDateOfRetirement().format(DATE_FMT) : null);
        r.setRetirementReason(entity.getRetirementReason());
        r.setDisclosures(entity.getDisclosures());
        return r;
    }

    public static List<DirectorRecord> toRecords(List<PspDirector> entities, String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
