package com.posgateway.aml.service.edgedata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.tunnel.EdgeDataException;
import com.posgateway.aml.edge.tunnel.TunnelError;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Writes records as CSV or JSON lines straight to an HTTP response stream (R6 exports): one record
 * at a time, flushed every {@value #FLUSH_EVERY} records, nothing buffered beyond the servlet's own
 * output buffer and nothing written to disk, a database or a cache (contract §7.14).
 *
 * <p>CSV follows RFC 4180 (quotes doubled; fields with a comma, quote or line break quoted) and
 * neutralises spreadsheet formulas: a text cell starting with {@code = + - @}, tab or CR is prefixed
 * with an apostrophe. Arrays become {@code ;}-joined cells. Numbers and booleans are written as is.
 */
public final class RecordStreamWriter {

    /** Export formats. */
    public enum Format {
        CSV("text/csv;charset=UTF-8", "csv"),
        NDJSON("application/x-ndjson;charset=UTF-8", "ndjson");

        private final String contentType;
        private final String extension;

        /** Binds the media type and file extension. */
        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        /** HTTP Content-Type. */
        public String contentType() {
            return contentType;
        }

        /** File extension for Content-Disposition. */
        public String extension() {
            return extension;
        }

        /** Parses {@code csv}, {@code ndjson} or {@code jsonl}; anything else is INVALID_PARAMS. */
        public static Format parse(String raw) {
            String f = raw == null || raw.isBlank() ? "csv" : raw.strip().toLowerCase(Locale.ROOT);
            return switch (f) {
                case "csv" -> CSV;
                case "ndjson", "jsonl", "json-lines" -> NDJSON;
                default -> throw new EdgeDataException(TunnelError.invalidParams("/format"));
            };
        }
    }

    /** Records between two flushes. */
    static final int FLUSH_EVERY = 200;

    private final OutputStream out;
    private final Format format;
    private final List<String> columns;
    private final ObjectMapper mapper;
    private long written;

    /**
     * @param columns CSV header / column order (NDJSON writes each record whole)
     */
    public RecordStreamWriter(OutputStream out, Format format, List<String> columns, ObjectMapper mapper) {
        this.out = out;
        this.format = format;
        this.columns = List.copyOf(columns);
        this.mapper = mapper;
    }

    /** Writes the CSV header row (a UTF-8 BOM first, for spreadsheet tools); no-op for NDJSON. */
    public void start() {
        if (format == Format.CSV) {
            write("﻿" + String.join(",", columns.stream().map(RecordStreamWriter::headerCell).toList()) + "\r\n");
        }
    }

    /** Writes one record. */
    public void record(JsonNode record) {
        if (format == Format.CSV) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    line.append(',');
                }
                line.append(cell(record.get(columns.get(i))));
            }
            write(line.append("\r\n").toString());
        } else {
            try {
                write(mapper.writeValueAsString(record) + "\n");
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("record could not be serialised");
            }
        }
        written++;
        if (written % FLUSH_EVERY == 0) {
            flush();
        }
    }

    /**
     * Ends the stream with its completeness: NDJSON always gets a final {@code {"@summary": …}}
     * line; CSV gets a final {@code # INCOMPLETE EXPORT …} line only when the export is incomplete,
     * so a complete CSV stays a plain table.
     */
    public void finish(ObjectNode summary, boolean incomplete, String incompleteNote) {
        if (format == Format.NDJSON) {
            ObjectNode line = mapper.createObjectNode();
            line.set("@summary", summary);
            try {
                write(mapper.writeValueAsString(line) + "\n");
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("summary could not be serialised");
            }
        } else if (incomplete) {
            write("# INCOMPLETE EXPORT: " + incompleteNote + "\r\n");
        }
        flush();
    }

    /** Records written so far. */
    public long written() {
        return written;
    }

    /** Flushes to the client. */
    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes UTF-8 text. */
    private void write(String s) {
        try {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One CSV cell from a header name. */
    private static String headerCell(String text) {
        return quote(text);
    }

    /** One CSV cell from a JSON value. */
    static String cell(JsonNode v) {
        if (v == null || v.isNull() || v.isMissingNode()) {
            return "";
        }
        if (v.isNumber() || v.isBoolean()) {
            return v.asText();
        }
        if (v.isArray()) {
            StringBuilder b = new StringBuilder();
            v.forEach(e -> {
                if (!b.isEmpty()) {
                    b.append(';');
                }
                b.append(e.isValueNode() ? e.asText() : e.toString());
            });
            return quote(neutralise(b.toString()));
        }
        return quote(neutralise(v.isValueNode() ? v.asText() : v.toString()));
    }

    /** Prefixes formula-looking text with an apostrophe. */
    private static String neutralise(String s) {
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            return "'" + s;
        }
        return s;
    }

    /** RFC 4180 quoting when needed. */
    private static String quote(String s) {
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
