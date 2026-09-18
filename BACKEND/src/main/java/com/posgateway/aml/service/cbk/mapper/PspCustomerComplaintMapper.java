package com.posgateway.aml.service.cbk.mapper;

import com.posgateway.aml.entity.psp.cbk.PspCustomerComplaint;
import com.posgateway.aml.integration.cbk.records.CustomerComplaintRecord;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps {@link PspCustomerComplaint} entities to {@link CustomerComplaintRecord} DTOs
 * for CBK GDI endpoint #5.
 */
public final class PspCustomerComplaintMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private PspCustomerComplaintMapper() {}

    public static CustomerComplaintRecord toRecord(PspCustomerComplaint entity, String institutionCode) {
        CustomerComplaintRecord r = new CustomerComplaintRecord();
        r.setPspId(institutionCode);
        r.setComplaintId(entity.getComplaintId());
        r.setComplaintCode(entity.getComplaintCode());
        r.setComplainantGender(entity.getComplainantGender());
        r.setComplaintFrequency(entity.getComplaintFrequency() != null
                ? entity.getComplaintFrequency().toString() : null);
        r.setComplainantName(entity.getComplainantName());
        r.setComplainantAge(entity.getComplainantAge() != null
                ? entity.getComplainantAge().toString() : null);
        r.setComplainantContactNumber(entity.getComplainantContactNumber());
        r.setComplainantSubCountyLocation(entity.getComplainantSubCountyLocation());
        r.setComplainantEducationLevel(entity.getComplainantEducationLevel());
        r.setOthersComplainantDetails(entity.getOthersComplainantDetails());
        r.setAgentId(entity.getAgentId());
        r.setDateOfOccurrence(entity.getDateOfOccurrence() != null
                ? entity.getDateOfOccurrence().format(DATE_FMT) : null);
        r.setDateReportedToTheInstitution(entity.getDateReportedToTheInstitution() != null
                ? entity.getDateReportedToTheInstitution().format(DATE_FMT) : null);
        r.setDateResolved(entity.getDateResolved() != null
                ? entity.getDateResolved().format(DATE_FMT) : null);
        r.setRemedialStatus(entity.getRemedialStatus());
        r.setAmountLost(entity.getAmountLost() != null
                ? entity.getAmountLost().toPlainString() : null);
        r.setAmountRecovered(entity.getAmountRecovered() != null
                ? entity.getAmountRecovered().toPlainString() : null);
        return r;
    }

    public static List<CustomerComplaintRecord> toRecords(List<PspCustomerComplaint> entities,
                                                           String institutionCode) {
        return entities.stream()
                .map(e -> toRecord(e, institutionCode))
                .collect(Collectors.toList());
    }
}
