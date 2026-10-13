package com.posgateway.aml.service.pagination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.dto.common.KeysetPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Generic keyset/offset pager for JPA repositories sorted by a timestamp and surrogate id column.
 */
@Service
public class KeysetSliceService {

    private final ObjectMapper objectMapper;

    public KeysetSliceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Fetches a page using either an opaque cursor or offset index against the provided spec. */
    public <T> KeysetPageResponse<T> slice(JpaSpecificationExecutor<T> repository,
                                           Specification<T> spec,
                                           int page,
                                           int size,
                                           String cursor,
                                           String timeProperty,
                                           String idProperty,
                                           Function<T, LocalDateTime> timeFn,
                                           Function<T, Long> idFn) {
        int safeSize = Math.max(1, Math.min(size, 100));
        Sort sort = Sort.by(Sort.Direction.DESC, timeProperty, idProperty);
        Specification<T> effective = spec;
        KeysetCursorCodec.CursorToken token = KeysetCursorCodec.decode(objectMapper, cursor);
        if (token != null) {
            effective = effective.and(keysetBefore(timeProperty, idProperty, token));
            List<T> slice = repository.findAll(effective, PageRequest.of(0, safeSize + 1, sort)).getContent();
            boolean hasNext = slice.size() > safeSize;
            List<T> content = hasNext ? slice.subList(0, safeSize) : slice;
            String next = hasNext && !content.isEmpty()
                    ? KeysetCursorCodec.encode(objectMapper, timeFn.apply(content.get(content.size() - 1)),
                    idFn.apply(content.get(content.size() - 1)))
                    : null;
            return KeysetPageResponse.of(content, safeSize, null, null, next);
        }
        int safePage = Math.max(0, page);
        Page<T> result = repository.findAll(effective, PageRequest.of(safePage, safeSize, sort));
        String next = result.hasNext() && !result.getContent().isEmpty()
                ? KeysetCursorCodec.encode(objectMapper,
                timeFn.apply(result.getContent().get(result.getContent().size() - 1)),
                idFn.apply(result.getContent().get(result.getContent().size() - 1)))
                : null;
        return KeysetPageResponse.of(result.getContent(), safeSize, result.getTotalElements(),
                result.getNumber(), next);
    }

    /** Builds a keyset predicate for descending sorts on (timeProperty, idProperty). */
    private <T> Specification<T> keysetBefore(String timeProperty,
                                                String idProperty,
                                                KeysetCursorCodec.CursorToken token) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get(timeProperty), token.sortTime()),
                cb.and(
                        cb.equal(root.get(timeProperty), token.sortTime()),
                        cb.lessThan(root.get(idProperty), token.sortId())));
    }
}
