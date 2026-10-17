import { getApiUrl } from "../config/api";
import {
  EdgeRequestError,
  buildQueryString,
  edgeHeaders,
  normaliseErrorBody,
  parseRetryAfter,
  withSupportQuery,
  type EdgeSupportParams,
  type QueryValue,
} from "./edgeData";

/**
 * Streamed exports of Edge-backed lists (contract section 7.14): the Control Plane writes the rows
 * straight through from the Edge and never stores them. Completeness arrives in the
 * {@code X-Edge-Incomplete} / {@code X-Edge-Unavailable-Sites} headers (sites already failed when
 * the stream opened) and in the last line (NDJSON {@code @summary}, or a CSV
 * {@code # INCOMPLETE EXPORT} line) for sites that failed during the stream.
 */

/** Export formats the server streams. */
export type EdgeExportFormat = "csv" | "ndjson";

/** Outcome of a finished export. */
export interface EdgeExportResult {
  blob: Blob;
  filename: string;
  incomplete: boolean;
  /** Human text naming the sites that did not answer, when known. */
  unavailableSites: string | null;
  /** Rows written, when the server reported it (NDJSON summary). */
  rows: number | null;
}

/** Completeness read from the response headers and the stream's final line. */
export interface ExportCompleteness {
  incomplete: boolean;
  unavailableSites: string | null;
  rows: number | null;
}

/** The last non-empty line of {@code text}. */
function lastLine(text: string): string {
  const trimmed = text.replace(/[\r\n]+$/, "");
  const index = Math.max(trimmed.lastIndexOf("\n"), trimmed.lastIndexOf("\r"));
  return index >= 0 ? trimmed.slice(index + 1) : trimmed;
}

/**
 * Reads completeness from the response headers and the tail of the body. {@code tail} need only
 * hold the end of the stream (the summary is always the final line).
 */
export function exportCompleteness(
  format: EdgeExportFormat,
  headers: { get(name: string): string | null },
  tail: string,
): ExportCompleteness {
  let incomplete = (headers.get("X-Edge-Incomplete") ?? "").toLowerCase() === "true";
  let unavailableSites = headers.get("X-Edge-Unavailable-Sites");
  let rows: number | null = null;
  const line = lastLine(tail);
  if (format === "ndjson" && line.startsWith("{\"@summary\"")) {
    try {
      const summary = (JSON.parse(line) as { "@summary"?: Record<string, unknown> })["@summary"] ?? {};
      if (summary.incomplete === true) incomplete = true;
      if (typeof summary.rows === "number") rows = summary.rows;
      const sites = summary.unavailableSites;
      if (Array.isArray(sites) && sites.length) {
        unavailableSites = sites
          .map((site) => (site && typeof site === "object" ? `${(site as { siteId?: string }).siteId ?? "?"} (${(site as { code?: string }).code ?? "?"})` : String(site)))
          .join(", ");
      }
    } catch {
      // A malformed final line is reported as an incomplete export below.
      incomplete = true;
    }
  } else if (format === "ndjson" && line.length > 0) {
    // NDJSON always ends with its summary; a missing one means the stream was cut.
    incomplete = true;
  }
  if (format === "csv" && line.startsWith("# INCOMPLETE EXPORT")) {
    incomplete = true;
    unavailableSites = line.replace(/^# INCOMPLETE EXPORT:?\s*/, "") || unavailableSites;
  }
  return { incomplete, unavailableSites, rows };
}

/** Filename from Content-Disposition, or a fallback. */
function filenameOf(disposition: string | null, fallback: string): string {
  const match = disposition?.match(/filename="([^"]+)"/);
  return match?.[1] ?? fallback;
}

/**
 * Streams an export into a Blob for download and reports whether it is complete. The rows are held
 * in memory only until the browser saves the file.
 */
export async function streamEdgeExport(
  path: string,
  format: EdgeExportFormat,
  query: Record<string, QueryValue>,
  support: EdgeSupportParams | null,
  signal?: AbortSignal,
): Promise<EdgeExportResult> {
  const fullQuery = withSupportQuery({ ...query, format }, support);
  const accept = format === "csv" ? "text/csv" : "application/x-ndjson";
  const response = await fetch(`${getApiUrl(path)}${buildQueryString(fullQuery)}`, {
    method: "GET",
    credentials: "include",
    cache: "no-store",
    headers: edgeHeaders(support, accept),
    signal,
  });
  if (!response.ok) {
    const raw = await response.json().catch(() => null);
    throw new EdgeRequestError(
      response.status,
      normaliseErrorBody(response.status, raw, parseRetryAfter(response.headers.get("Retry-After"))),
    );
  }
  const chunks: Uint8Array[] = [];
  let tail = "";
  const decoder = new TextDecoder();
  if (response.body) {
    const reader = response.body.getReader();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value) {
        chunks.push(value);
        tail = (tail + decoder.decode(value, { stream: true })).slice(-8192);
      }
    }
  } else {
    const text = await response.text();
    chunks.push(new TextEncoder().encode(text));
    tail = text.slice(-8192);
  }
  const completeness = exportCompleteness(format, response.headers, tail);
  return {
    blob: new Blob(chunks as BlobPart[], { type: accept }),
    filename: filenameOf(response.headers.get("Content-Disposition"), `export.${format === "csv" ? "csv" : "ndjson"}`),
    ...completeness,
  };
}
