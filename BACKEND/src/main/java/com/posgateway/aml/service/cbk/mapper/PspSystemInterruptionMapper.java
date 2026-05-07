package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspSystemInterruption;
import com.posgateway.aml.integration.cbk.records.SystemStabilityRecord;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspSystemInterruption} entities to {@link SystemStabilityRecord} DTOs
 * for CBK GDI endpoint #8.
 *
 * <p>Field name note: entity column {@code system_unavailability_type_code} maps to
 * record field {@code SYSTEM_UNAVAILABILITY_TYPE_COD} (truncated CBK name).
 * Similarly {@code service_interruption_cause_code} → {@code SERVICE_INTERRUPTION_CAUSE_COD}.
 */
public final class PspSystemInterruptionMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspSystemInterruptionMapper() {}

    public static SystemStabilityRecord toRecord(PspSystemInterruption entity, String institutionCode) {
        SystemStabilityRecord r = new SystemStabilityRecord();
        r.setPspId(institutionCode);
        r.setReportingDate(entity.getReportingDate() != null
                ? entity.getReportingDate().format(DATE_FMT)
                : LocalDate.now().minusDays(1).format(DATE_FMT));
        r.setSubCountyCode(entity.getSubCountyCode());
        r.setSystemOwnerFlag(entity.getSystemOwnerFlag());
        r.setThirdPartyOwnedCategory(entity.getThirdPartyOwnedCategory());
        r.setThirdPartyName(entity.getThirdPartyName());
        r.setProductType(entity.getProductType());
        r.setSystemUnavailabilityTypeCod(entity.getSystemUnavailabilityTypeCode());
        r.setThirdPartySystemAffected(entity.getThirdPartySystemAffected());
        r.setServiceInterruptionCauseCod(entity.getServiceInterruptionCauseCode());
        r.setSeverityInterruptionCode(entity.getSeverityInterruptionCode());
        r.setRecoveryTimeCode(entity.getRecoveryTimeCode());
        r.setRemedialStatusCode(entity.getRemedialStatusCode());
        r.setSystemUptimePercentage(entity.getSystemUptimePercentage() != null
                ? entity.getSystemUptimePercentage().toPlainString() : null);
        return r;
    }

    public static List<SystemStabilityRecord> toRecords(List<PspSystemInterruption> entities,
                                                         String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
