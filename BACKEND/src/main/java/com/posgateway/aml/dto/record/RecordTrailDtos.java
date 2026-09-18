package com.posgateway.aml.dto.record;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Stable, UI-neutral contract for following an investigation record trail. */
public final class RecordTrailDtos {
    private RecordTrailDtos() {}

    public enum RecordType {
        ALERT,
        CASE,
        TRANSACTION,
        MERCHANT,
        MULTI_ASSET_CUSTOMER,
        MULTI_ASSET_TRANSACTION,
        REPORT_EXECUTION
    }

    public record RecordLink(
            String recordType,
            String recordId,
            String label,
            String relationship,
            Map<String, Object> summary) {}

    public record RecordOccurrence(
            String occurrenceType,
            LocalDateTime occurredAt,
            String description,
            Map<String, Object> data,
            RecordLink source) {}

    public record RecordDetail(
            String recordType,
            String recordId,
            String title,
            Map<String, Object> data,
            List<RecordLink> relatedRecords,
            List<RecordOccurrence> occurrences) {}
}
