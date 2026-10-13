package com.posgateway.aml.dto.common;

import java.util.List;

/**
 * API page envelope that supports both offset totals and an opaque keyset cursor for the next slice.
 */
public record KeysetPageResponse<T>(
        List<T> content,
        int size,
        Long totalElements,
        Integer totalPages,
        Integer number,
        String nextCursor,
        boolean hasNext
) {
    /** Builds a keyset page from a content slice and optional cursor metadata. */
    public static <T> KeysetPageResponse<T> of(List<T> content,
                                               int size,
                                               Long totalElements,
                                               Integer number,
                                               String nextCursor) {
        boolean hasNext = nextCursor != null && !nextCursor.isBlank();
        Integer totalPages = totalElements != null && size > 0
                ? (int) Math.ceil((double) totalElements / size)
                : null;
        return new KeysetPageResponse<>(content, size, totalElements, totalPages, number, nextCursor, hasNext);
    }
}
