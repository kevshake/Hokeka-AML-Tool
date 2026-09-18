package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspFraudIncident;
import com.posgateway.aml.integration.cbk.records.FraudIncidentRecord;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspFraudIncident} entities to {@link FraudIncidentRecord} DTOs
 * for CBK GDI endpoint #7.
 */
public final class PspFraudIncidentMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspFraudIncidentMapper() {}

    public static FraudIncidentRecord toRecord(PspFraudIncident entity, String institutionCode) {
        FraudIncidentRecord r = new FraudIncidentRecord();
        r.setPspId(institutionCode);
        r.setReportingDate(entity.getReportingDate() != null
                ? entity.getReportingDate().format(DATE_FMT)
                : LocalDate.now().minusDays(1).format(DATE_FMT));
        r.setSubCountyCode(entity.getSubCountyCode());
        r.setSubFraudCode(entity.getSubFraudCode());
        r.setFraudCategoryFlag(entity.getFraudCategoryFlag());
        r.setVictimCategory(entity.getVictimCategory());
        r.setVictimInformation(entity.getVictimInformation());
        r.setDateOfOccurrence(entity.getDateOfOccurrence() != null
                ? entity.getDateOfOccurrence().format(DATE_FMT) : null);
        r.setNumberOfIncidences(entity.getNumberOfIncidences() != null
                ? entity.getNumberOfIncidences().toString() : null);
        r.setAmountInvolved(entity.getAmountInvolved() != null
                ? entity.getAmountInvolved().toPlainString() : null);
        r.setAmountLost(entity.getAmountLost() != null
                ? entity.getAmountLost().toPlainString() : null);
        r.setAmountRecovered(entity.getAmountRecovered() != null
                ? entity.getAmountRecovered().toPlainString() : null);
        r.setActionTaken(entity.getActionTaken());
        r.setRecoveryDetails(entity.getRecoveryDetails());
        return r;
    }

    public static List<FraudIncidentRecord> toRecords(List<PspFraudIncident> entities,
                                                       String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
