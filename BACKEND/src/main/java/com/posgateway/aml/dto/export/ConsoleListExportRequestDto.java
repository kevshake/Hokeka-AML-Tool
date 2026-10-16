package com.posgateway.aml.dto.export;

import java.util.Map;

/**
 * Request body for starting a Console list export with the same filter map the UI encodes in the URL.
 */
public record ConsoleListExportRequestDto(
        ConsoleListViewType viewType,
        String format,
        Map<String, String> filters,
        boolean includeCorrelated,
        String recordType,
        String recordId
) {
}
