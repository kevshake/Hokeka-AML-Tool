package com.posgateway.aml.dto.export;

/**
 * Returned when an export is queued asynchronously instead of streamed inline.
 */
public record ConsoleListExportStartResponse(
        String jobUuid,
        String status,
        Long estimatedRows
) {
}
