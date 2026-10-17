package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelError;
import com.posgateway.aml.edge.tunnel.TunnelErrorCode;
import com.posgateway.aml.edge.tunnel.TunnelTime;
import com.posgateway.aml.entity.edge.EdgeDataAccessLog;
import com.posgateway.aml.repository.edge.EdgeDataAccessLogRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * {@code GET /api/v1/edge-data/access-log} and its export (R6, contract §7.15): the PSP-visible
 * record of every pull of its data, server-side paginated and filtered, newest first. A PSP user
 * sees its own PSP's rows; platform staff see one named PSP's rows at a time. Rows are read-only:
 * nothing here updates or deletes them.
 */
@Service
public class EdgeDataAccessLogService {

    /** Largest page the Console may ask for. */
    public static final int MAX_PAGE = 200;
    /** Rows per database page while exporting. */
    private static final int EXPORT_PAGE = 500;
    /** Export cap (the log is append-only; a filter narrows it). */
    public static final int MAX_EXPORT_ROWS = 1_000_000;

    static final List<String> COLUMNS = List.of("auditId", "requestedAt", "completedAt", "actorKind", "actorUserId",
            "actorRole", "purpose", "caseId", "alertId", "filingRef", "supportAccess", "supportReason", "op",
            "paramsSummary", "paramsDigest", "sitesContacted", "sitesAnswered", "itemCount", "bytes", "outcome");

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final EdgeDataCallerResolver callers;
    private final EdgeDataAccessLogRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    /** Wires the access-log reader. */
    public EdgeDataAccessLogService(EdgeDataCallerResolver callers, EdgeDataAccessLogRepository repository,
                                    ObjectMapper mapper) {
        this.callers = callers;
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Filters of the log view.
     *
     * @param from          requested at or after (RFC 3339)
     * @param to            requested before
     * @param op            tunnel op ({@code txn.search}, …)
     * @param actorKind     {@code PSP_USER}, {@code PLATFORM_USER}, {@code SYSTEM}
     * @param supportAccess only support-access pulls ({@code true}) or only others ({@code false})
     * @param outcome       {@code OK}, {@code PARTIAL} or an error code
     */
    public record Filter(String from, String to, String op, String actorKind, Boolean supportAccess, String outcome) {}

    /** One page as {@code {content, totalElements, totalPages, size, number}} (the Console page shape). */
    public ObjectNode page(Long requestedPspId, Filter filter, int page, int size) {
        long psp = pspOf(requestedPspId);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE));
        Page<EdgeDataAccessLog> rows = repository.findAll(spec(psp, filter),
                PageRequest.of(Math.max(0, page), safeSize, order()));
        ObjectNode out = mapper.createObjectNode();
        ArrayNode content = out.putArray("content");
        rows.getContent().forEach(r -> content.add(toJson(r)));
        out.put("totalElements", rows.getTotalElements());
        out.put("totalPages", rows.getTotalPages());
        out.put("size", rows.getSize());
        out.put("number", rows.getNumber());
        out.put("pspId", psp);
        return out;
    }

    /** Streams every matching row (newest first) as CSV or JSON lines, page by page. */
    public void export(Long requestedPspId, Filter filter, String rawFormat, HttpServletResponse response) {
        RecordStreamWriter.Format format = RecordStreamWriter.Format.parse(rawFormat);
        long psp = pspOf(requestedPspId);
        Specification<EdgeDataAccessLog> spec = spec(psp, filter);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(format.contentType());
        response.setHeader("Content-Disposition", "attachment; filename=\"edge-data-access-log-" + psp + "-"
                + FILE_DATE.format(clock.instant()) + "." + format.extension() + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        RecordStreamWriter writer;
        try {
            writer = new RecordStreamWriter(response.getOutputStream(), format, COLUMNS, mapper);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        writer.start();
        int page = 0;
        while (writer.written() < MAX_EXPORT_ROWS) {
            Page<EdgeDataAccessLog> rows = repository.findAll(spec, PageRequest.of(page++, EXPORT_PAGE, order()));
            for (EdgeDataAccessLog r : rows.getContent()) {
                if (writer.written() >= MAX_EXPORT_ROWS) {
                    break;
                }
                writer.record(toJson(r));
            }
            if (!rows.hasNext()) {
                break;
            }
        }
        ObjectNode summary = mapper.createObjectNode().put("rows", writer.written()).put("incomplete", false);
        writer.finish(summary, false, "");
    }

    /** Newest first, ties by id. */
    private static Sort order() {
        return Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"));
    }

    /** The PSP whose log is read: own PSP for PSP users, the named PSP for platform staff. */
    private long pspOf(Long requestedPspId) {
        EdgeDataCaller caller = callers.current();
        if (caller.pspUser()) {
            if (requestedPspId != null && !requestedPspId.equals(caller.pspId())) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH,
                        "a PSP user can only read its own PSP"));
            }
            return caller.pspId();
        }
        if (requestedPspId == null || requestedPspId <= 0) {
            throw new EdgeDataException(TunnelError.invalidParams("/pspId"));
        }
        return requestedPspId;
    }

    /** PSP plus the optional filters (validated; bad values are INVALID_PARAMS by pointer). */
    private static Specification<EdgeDataAccessLog> spec(long psp, Filter f) {
        Specification<EdgeDataAccessLog> s = (root, q, cb) -> cb.equal(root.get("pspId"), psp);
        if (f == null) {
            return s;
        }
        if (f.from() != null && !f.from().isBlank()) {
            Instant from = instant(f.from(), "/from");
            s = s.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("requestedAt"), from));
        }
        if (f.to() != null && !f.to().isBlank()) {
            Instant to = instant(f.to(), "/to");
            s = s.and((root, q, cb) -> cb.lessThan(root.get("requestedAt"), to));
        }
        if (f.op() != null && !f.op().isBlank()) {
            String op = f.op().strip();
            s = s.and((root, q, cb) -> cb.equal(root.get("op"), op));
        }
        if (f.actorKind() != null && !f.actorKind().isBlank()) {
            String kind = f.actorKind().strip().toUpperCase(Locale.ROOT);
            s = s.and((root, q, cb) -> cb.equal(root.get("actorKind"), kind));
        }
        if (f.supportAccess() != null) {
            boolean support = f.supportAccess();
            s = s.and((root, q, cb) -> cb.equal(root.get("supportAccess"), support));
        }
        if (f.outcome() != null && !f.outcome().isBlank()) {
            String outcome = f.outcome().strip().toUpperCase(Locale.ROOT);
            s = s.and((root, q, cb) -> cb.equal(root.get("outcome"), outcome));
        }
        return s;
    }

    /** Parses a filter instant. */
    private static Instant instant(String raw, String pointer) {
        try {
            return Instant.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw new EdgeDataException(TunnelError.invalidParams(pointer));
        }
    }

    /** One log row as the Console reads it (the summary parsed back into JSON). */
    ObjectNode toJson(EdgeDataAccessLog r) {
        ObjectNode n = mapper.createObjectNode();
        n.put("auditId", r.getAuditId().toString());
        n.put("requestedAt", r.getRequestedAt() == null ? null : TunnelTime.formatMillis(r.getRequestedAt()));
        n.put("completedAt", r.getCompletedAt() == null ? null : TunnelTime.formatMillis(r.getCompletedAt()));
        n.put("actorKind", r.getActorKind());
        n.put("actorUserId", r.getActorUserId());
        n.put("actorRole", r.getActorRole());
        n.put("purpose", r.getPurpose());
        n.put("caseId", r.getCaseId());
        n.put("alertId", r.getAlertId());
        n.put("filingRef", r.getFilingRef());
        n.put("supportAccess", r.isSupportAccess());
        n.put("supportReason", r.getSupportReason());
        n.put("op", r.getOp());
        n.set("paramsSummary", summary(r.getParamsSummary()));
        n.put("paramsDigest", r.getParamsDigest());
        n.set("sitesContacted", sites(r.getSitesContacted()));
        n.set("sitesAnswered", sites(r.getSitesAnswered()));
        n.put("itemCount", r.getItemCount());
        n.put("bytes", r.getBytes());
        n.put("outcome", r.getOutcome());
        return n;
    }

    /** The stored summary as JSON (it is written as JSON by the auditor). */
    private JsonNode summary(String text) {
        if (text == null || text.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(text);
        } catch (IOException e) {
            return mapper.getNodeFactory().textNode(text);
        }
    }

    /** Comma-joined site ids as an array. */
    private ArrayNode sites(String joined) {
        ArrayNode a = mapper.createArrayNode();
        if (joined != null && !joined.isBlank()) {
            for (String s : joined.split(",")) {
                a.add(s);
            }
        }
        return a;
    }
}
