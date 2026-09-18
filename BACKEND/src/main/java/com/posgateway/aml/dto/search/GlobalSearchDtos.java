package com.posgateway.aml.dto.search;

import java.util.List;

public final class GlobalSearchDtos {

    private GlobalSearchDtos() {}

    public record GlobalSearchHit(
            String entityType,
            String entityId,
            String title,
            String subtitle,
            String status,
            String recordPath) {}

    public record GlobalSearchResponse(
            String query,
            long totalHits,
            List<GlobalSearchHit> hits) {}
}
