package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.EdgeDataResult;
import com.posgateway.aml.edge.tunnel.EdgeDataStream;
import com.posgateway.aml.edge.tunnel.TunnelActor;
import com.posgateway.aml.edge.tunnel.TunnelError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@code GET /api/v1/transactions/export} (R6): streams the PSP's transactions matching a filter to
 * the client as CSV or JSON lines, never storing them (contract §7.14, §7.16). For an Edge PSP it
 * reads {@code export.stream} under credit (366-day span, at most 1 000 000 rows), merging sites
 * chunk-wise; for a PSP without an Edge it pages its legacy rows. Either way the export is one
 * access-log row.
 *
 * <p><b>Completeness.</b> The stream is opened before the response is committed, so an export no
 * site can serve is an ordinary §7.11 error. Sites already failed at open are named in the
 * {@code X-Edge-Incomplete} / {@code X-Edge-Unavailable-Sites} headers; a site failing later is
 * reported at the end of the body (NDJSON {@code @summary} line, CSV {@code # INCOMPLETE EXPORT}
 * line) and in the HTTP trailer fields when the client accepts trailers.
 */
@Service
public class TransactionExportService {

    /** Fields every export row carries (§7.6 export.stream). */
    static final List<String> BASE_COLUMNS = List.of("edgeAssessmentId", "evaluatedAt", "siteId", "decision");

    /** Projection columns of {@code export.stream} (summary fields). */
    static final List<String> PROJECTION_COLUMNS = List.of("txnRef", "nodeId", "score", "merchantId", "mcc",
            "amountMinor", "currency", "countryCode", "channel", "triggeredRuleIds", "ctrRequired", "sanctionsHit",
            "origin");

    /** Legacy rows have no site or Edge fields: their own fixed column set. */
    static final List<String> LEGACY_COLUMNS = List.of("txnId", "evaluatedAt", "decision", "txnRef", "merchantId",
            "terminalId", "amountMinor", "currency", "countryCode", "channel", "riskLevel", "ctrRequired", "source");

    /** §7.6 export.stream default and maximum rows. */
    static final int DEFAULT_MAX_ROWS = 100_000;
    static final int MAX_ROWS = 1_000_000;

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final EdgeDataCallerResolver callers;
    private final TransactionSourceResolver sources;
    private final EdgeTransactionProxyService edge;
    private final LegacyTransactionReadService legacy;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    /** Wires the exporter. */
    public TransactionExportService(EdgeDataCallerResolver callers, TransactionSourceResolver sources,
                                    EdgeTransactionProxyService edge, LegacyTransactionReadService legacy,
                                    ObjectMapper mapper) {
        this.callers = callers;
        this.sources = sources;
        this.edge = edge;
        this.legacy = legacy;
        this.mapper = mapper;
    }

    /**
     * Streams the export to {@code response}.
     *
     * @throws EdgeDataException before anything is written when the export cannot start
     */
    public void export(TransactionReadService.Access access, TransactionFilter filter, String rawFormat,
                       List<String> rawColumns, Integer rawMaxRows, HttpServletResponse response) {
        RecordStreamWriter.Format format = RecordStreamWriter.Format.parse(rawFormat);
        List<String> projection = columns(rawColumns);
        int maxRows = maxRows(rawMaxRows);
        EdgeDataScope scope = callers.scope(access.pspId(), access.supportReason(),
                access.purpose() != null ? access.purpose() : TunnelActor.Purpose.DATA_EXPORT,
                access.refs() == null ? EdgeDataCallerResolver.WorkflowRefs.NONE : access.refs());
        String fileName = "transactions-" + scope.pspId() + "-" + FILE_DATE.format(clock.instant()) + "."
                + format.extension();
        if (sources.sourceFor(scope.pspId()) == TransactionSource.CLOUD_LEGACY) {
            exportLegacy(scope, filter, format, maxRows, fileName, response);
            return;
        }
        ObjectNode params = JsonNodeFactory.instance.objectNode();
        params.set("filter", filter.toJson());
        if (!projection.isEmpty()) {
            ArrayNode c = params.putArray("columns");
            projection.forEach(c::add);
        }
        params.put("maxRows", maxRows);
        List<String> header = new ArrayList<>(BASE_COLUMNS);
        header.addAll(projection.isEmpty() ? PROJECTION_COLUMNS : projection);
        header.add("source");
        try (EdgeDataStream stream = edge.openExport(scope, params)) {
            List<EdgeDataResult.UnavailableSite> atOpen = stream.unavailableSites();
            begin(response, format, fileName, atOpen);
            RecordStreamWriter writer = new RecordStreamWriter(output(response), format, header, mapper);
            installTrailers(response, () -> stream.unavailableSites());
            writer.start();
            while (stream.hasNext()) {
                writer.record(EdgeConsoleView.edgeItem(stream.next()));
            }
            List<EdgeDataResult.UnavailableSite> unavailable = stream.unavailableSites();
            writer.finish(summary(writer.written(), unavailable, stream.totalRows()), !unavailable.isEmpty(),
                    note(writer.written(), unavailable));
        }
    }

    /** The legacy-row export of a PSP without an Edge (same formats, legacy columns). */
    private void exportLegacy(EdgeDataScope scope, TransactionFilter filter, RecordStreamWriter.Format format,
                              int maxRows, String fileName, HttpServletResponse response) {
        RecordStreamWriter[] writer = new RecordStreamWriter[1];
        legacy.export(scope, filter, maxRows, item -> {
            if (writer[0] == null) {
                writer[0] = open(response, format, fileName);
            }
            writer[0].record(item);
        });
        if (writer[0] == null) {
            writer[0] = open(response, format, fileName);
        }
        writer[0].finish(summary(writer[0].written(), List.of(), null), false, "");
    }

    /** Commits the headers and writes the CSV header row of a legacy export. */
    private RecordStreamWriter open(HttpServletResponse response, RecordStreamWriter.Format format, String fileName) {
        begin(response, format, fileName, List.of());
        RecordStreamWriter w = new RecordStreamWriter(output(response), format, LEGACY_COLUMNS, mapper);
        w.start();
        return w;
    }

    /** Status, type, disposition, no-store and the at-open completeness headers. */
    private static void begin(HttpServletResponse response, RecordStreamWriter.Format format, String fileName,
                              List<EdgeDataResult.UnavailableSite> atOpen) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(format.contentType());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!atOpen.isEmpty()) {
            response.setHeader("X-Edge-Incomplete", "true");
            response.setHeader("X-Edge-Unavailable-Sites", sitesText(atOpen));
        }
    }

    /** Trailer fields with the final completeness (sent only to clients that accept trailers). */
    private static void installTrailers(HttpServletResponse response,
                                        Supplier<List<EdgeDataResult.UnavailableSite>> unavailable) {
        try {
            response.setTrailerFields(() -> {
                List<EdgeDataResult.UnavailableSite> u = unavailable.get();
                return u.isEmpty() ? Map.of("X-Edge-Incomplete", "false")
                        : Map.of("X-Edge-Incomplete", "true", "X-Edge-Unavailable-Sites", sitesText(u));
            });
        } catch (IllegalStateException e) {
            // HTTP/1.0 or an already committed response: completeness is still in the body.
        }
    }

    /** The response body stream. */
    private static OutputStream output(HttpServletResponse response) {
        try {
            return response.getOutputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code siteA:EDGE_OFFLINE,siteB:EDGE_TIMEOUT} (site ids and codes only). */
    private static String sitesText(List<EdgeDataResult.UnavailableSite> sites) {
        return sites.stream().map(s -> (s.siteId() == null ? "-" : s.siteId()) + ":" + s.code().name())
                .collect(Collectors.joining(","));
    }

    /** NDJSON summary: rows, completeness, unavailable sites, the Edges' own total. */
    private ObjectNode summary(long rows, List<EdgeDataResult.UnavailableSite> unavailable, Long edgeTotal) {
        ObjectNode s = mapper.createObjectNode();
        s.put("rows", rows);
        s.put("incomplete", !unavailable.isEmpty());
        ArrayNode u = s.putArray("unavailableSites");
        unavailable.forEach(site -> u.add(site.toJson()));
        if (edgeTotal != null) {
            s.put("edgeTotalRows", edgeTotal);
        }
        return s;
    }

    /** CSV incompleteness note. */
    private static String note(long rows, List<EdgeDataResult.UnavailableSite> unavailable) {
        return rows + " rows; unavailable sites: " + sitesText(unavailable);
    }

    /** Requested projection columns (validated against export.stream's enum), deduplicated. */
    static List<String> columns(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            String c = raw.get(i) == null ? "" : raw.get(i).strip();
            if (BASE_COLUMNS.contains(c)) {
                continue;
            }
            if (!PROJECTION_COLUMNS.contains(c)) {
                throw new EdgeDataException(TunnelError.invalidParams("/columns/" + i));
            }
            out.add(c);
        }
        return List.copyOf(out);
    }

    /** maxRows 1..1 000 000, default 100 000. */
    static int maxRows(Integer raw) {
        if (raw == null) {
            return DEFAULT_MAX_ROWS;
        }
        if (raw < 1 || raw > MAX_ROWS) {
            throw new EdgeDataException(TunnelError.invalidParams("/maxRows"));
        }
        return raw;
    }
}
