package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspCyberIncident;
import com.posgateway.aml.integration.cbk.records.CyberIncidentRecord;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspCyberIncident} entities to {@link CyberIncidentRecord} DTOs
 * for CBK GDI endpoint #6.
 *
 * <p>Field name notes:
 * <ul>
 *   <li>Entity {@code incidentDate}    → record {@code DATE_AND_TIME_OF_INCIDENT_HAPPENED}</li>
 *   <li>Entity {@code details}         → record {@code DETAILS_OF_THE_INCIDENT}</li>
 *   <li>Entity {@code actionTaken}     → record {@code ACTION_TAKEN_TO_MANAGE_THE_INCIDENT}</li>
 *   <li>Entity {@code resolutionDate}  → record {@code DATE_AND_TIME_OF_THE_INCIDENT_RESOLUTION}</li>
 *   <li>Entity {@code mitigationActions} → record {@code ACTION_TAKEN_TO_MITIGATE_FUTURE_INCIDENTS}</li>
 * </ul>
 * The record {@code REPORTING_DATE} is set to yesterday (matches daily cadence).
 */
public final class PspCyberIncidentMapper {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspCyberIncidentMapper() {}

    public static CyberIncidentRecord toRecord(PspCyberIncident entity, String institutionCode) {
        CyberIncidentRecord r = new CyberIncidentRecord();
        r.setPspId(institutionCode);
        r.setIncidentNumber(entity.getIncidentNumber());
        r.setReportingDate(LocalDate.now().minusDays(1).format(DATE_FMT));
        r.setLocationOfAttacker(entity.getLocationOfAttacker());
        r.setIncidentMode(entity.getIncidentMode());
        r.setDateAndTimeOfIncidentHappened(entity.getIncidentDate() != null
                ? entity.getIncidentDate().format(DATE_TIME_FMT) : null);
        r.setLossType(entity.getLossType());
        r.setDetailsOfTheIncident(entity.getDetails());
        r.setActionTakenToManageTheIncident(entity.getActionTaken());
        r.setDateAndTimeOfTheIncidentResolution(entity.getResolutionDate() != null
                ? entity.getResolutionDate().format(DATE_TIME_FMT) : null);
        r.setActionTakenToMitigateFutureIncidents(entity.getMitigationActions());
        r.setAmountInvolved(entity.getAmountInvolved() != null
                ? entity.getAmountInvolved().toPlainString() : null);
        r.setAmountLost(entity.getAmountLost() != null
                ? entity.getAmountLost().toPlainString() : null);
        return r;
    }

    public static List<CyberIncidentRecord> toRecords(List<PspCyberIncident> entities,
                                                       String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
