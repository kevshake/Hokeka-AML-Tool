package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspSeniorManagement;
import com.posgateway.aml.integration.cbk.records.SeniorManagementRecord;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspSeniorManagement} entities to {@link SeniorManagementRecord} DTOs
 * for CBK GDI endpoint #1.
 */
public final class PspSeniorManagementMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspSeniorManagementMapper() {}

    public static SeniorManagementRecord toRecord(PspSeniorManagement entity, String institutionCode) {
        SeniorManagementRecord r = new SeniorManagementRecord();
        r.setPspId(institutionCode);
        r.setOfficerNames(entity.getOfficerNames());
        r.setGender(entity.getGender());
        r.setDesignation(entity.getDesignation());
        r.setDob(entity.getDob() != null ? entity.getDob().format(DATE_FMT) : null);
        r.setNationality(entity.getNationality());
        r.setIdNo(entity.getIdNo());
        r.setTaxId(entity.getTaxId());
        r.setQualification(entity.getQualification());
        r.setDateOfEmp(entity.getDateOfEmp() != null ? entity.getDateOfEmp().format(DATE_FMT) : null);
        r.setEmpType(entity.getEmpType());
        r.setRetirementDt(entity.getRetirementDt() != null
                ? entity.getRetirementDt().format(DATE_FMT) : null);
        r.setExternalAffliates(entity.getExternalAffliates());
        r.setOtherDisclosure(entity.getOtherDisclosure());
        return r;
    }

    public static List<SeniorManagementRecord> toRecords(List<PspSeniorManagement> entities,
                                                          String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
