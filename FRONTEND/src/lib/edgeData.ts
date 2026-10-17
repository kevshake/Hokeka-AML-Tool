import { getApiUrl } from "../config/api";
import type {
  EdgeDataError,
  EdgeDataErrorCode,
  EdgePullStatus,
  EdgeUnavailableSite,
} from "../types";

/**
 * Live pulls of transaction data from a PSP's Edge (contract section 7.14). The Control Plane holds
 * no transaction data: every request here goes through the Edge tunnel and the answer lives only in
 * React Query memory for a short time. Nothing in this module writes to localStorage,
 * sessionStorage, IndexedDB or any other browser store.
 */

/** Console state of a live Edge pull. */
export type EdgeDataState =
  | "idle"
  | "loading"
  | "ready"
  | "partial"
  | "offline"
  | "timeout"
  | "busy"
  | "tenant-error"
  | "support-required"
  | "support-disabled"
  | "not-found"
  | "error";

/** Header carrying a platform admin's support reason (contract section 9). */
export const SUPPORT_REASON_HEADER = "X-Support-Reason";

/** Support reasons must be 10..500 characters (contract section 9). */
export const SUPPORT_REASON_MIN = 10;
export const SUPPORT_REASON_MAX = 500;

/** One support session covers at most 60 minutes of pulls for one ticket (contract section 9). */
export const SUPPORT_SESSION_MS = 60 * 60_000;

/** React Query keeps pulled data only briefly after the last view unmounts. */
export const EDGE_DATA_GC_TIME_MS = 30_000;

/** Query key root of every live Edge pull; cleared when a support session ends. */
export const EDGE_DATA_QUERY_ROOT = "edge-data";

/** Error thrown for a failed tunnel-backed request, carrying the section 7.11 body. */
export class EdgeRequestError extends Error {
  readonly status: number;
  readonly body: EdgeDataError;

  /** Wraps the HTTP status and the parsed (or synthesised) error body. */
  constructor(status: number, body: EdgeDataError) {
    super(body.message || `Request failed with status ${status}`);
    this.name = "EdgeRequestError";
    this.status = status;
    this.body = body;
  }
}

/** Platform-admin context sent with a pull: the PSP being supported and the reason. */
export interface EdgeSupportParams {
  pspId?: number | null;
  reason?: string | null;
}

/** Options of one tunnel-backed request. */
export interface EdgeRequestOptions {
  method?: "GET" | "POST";
  query?: Record<string, QueryValue>;
  body?: unknown;
  support?: EdgeSupportParams | null;
  signal?: AbortSignal;
}

/** A query parameter value; arrays repeat the key, empty values are dropped. */
export type QueryValue = string | number | boolean | null | undefined | ReadonlyArray<string | number>;

/** Builds a query string from {@code params}, repeating array keys and skipping empty values. */
export function buildQueryString(params: Record<string, QueryValue> | undefined): string {
  if (!params) return "";
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (item !== "" && item !== null && item !== undefined) search.append(key, String(item));
      });
      return;
    }
    search.append(key, String(value));
  });
  const text = search.toString();
  return text ? `?${text}` : "";
}

/** Reads the in-memory PSP id the apiClient keeps for the X-PSP-ID header (0 when unknown). */
function currentPspHeader(): string {
  const client = (globalThis as { apiClient?: { pspIdCache?: number | null } }).apiClient;
  const cached = client?.pspIdCache;
  return typeof cached === "number" && Number.isFinite(cached) ? String(cached) : "0";
}

/** Parses Retry-After (seconds or HTTP date) into milliseconds. */
export function parseRetryAfter(value: string | null): number | undefined {
  if (!value) return undefined;
  const seconds = Number(value);
  if (Number.isFinite(seconds) && seconds >= 0) return Math.round(seconds * 1000);
  const date = Date.parse(value);
  if (Number.isNaN(date)) return undefined;
  return Math.max(0, date - Date.now());
}

/** Turns any error body into a section 7.11 body, synthesising a code from the HTTP status. */
export function normaliseErrorBody(status: number, raw: unknown, retryAfterMs?: number): EdgeDataError {
  const body = (raw && typeof raw === "object" ? raw : {}) as Partial<EdgeDataError> & { error?: string };
  const code = (typeof body.code === "string" ? body.code : codeForStatus(status)) as EdgeDataErrorCode;
  return {
    code,
    httpStatus: typeof body.httpStatus === "number" ? body.httpStatus : status,
    message: typeof body.message === "string" && body.message ? body.message : body.error ?? "",
    retryable: typeof body.retryable === "boolean" ? body.retryable : status === 429 || status >= 503,
    retryAfterMs: typeof body.retryAfterMs === "number" ? body.retryAfterMs : retryAfterMs,
    lastSeenAt: body.lastSeenAt,
    siteId: body.siteId,
    param: body.param,
  };
}

/** The section 7.11 code a bare HTTP status most likely means. */
function codeForStatus(status: number): EdgeDataErrorCode {
  switch (status) {
    case 400:
      return "INVALID_PARAMS";
    case 403:
      return "TENANT_MISMATCH";
    case 404:
      return "NOT_FOUND";
    case 429:
      return "EDGE_BUSY";
    case 503:
      return "EDGE_OFFLINE";
    case 504:
      return "EDGE_TIMEOUT";
    default:
      return "INTERNAL";
  }
}

/** Request headers of a tunnel-backed call: JSON, the PSP header and the support reason. */
export function edgeHeaders(support?: EdgeSupportParams | null, accept = "application/json"): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: accept,
    "Content-Type": "application/json",
    "X-PSP-ID": currentPspHeader(),
  };
  const reason = support?.reason?.trim();
  if (reason) headers[SUPPORT_REASON_HEADER] = reason;
  return headers;
}

/** Adds the supported PSP to the query of a platform-admin pull (PSP users never send one). */
export function withSupportQuery(
  query: Record<string, QueryValue> | undefined,
  support?: EdgeSupportParams | null,
): Record<string, QueryValue> {
  const out: Record<string, QueryValue> = { ...(query ?? {}) };
  if (support?.pspId != null && support.pspId > 0 && out.pspId === undefined) out.pspId = support.pspId;
  return out;
}

/**
 * Performs one tunnel-backed request. Throws {@link EdgeRequestError} with the section 7.11 body
 * on any non-2xx answer. The browser cache is bypassed ({@code cache: "no-store"}).
 */
export async function edgeRequest<T>(path: string, options: EdgeRequestOptions = {}): Promise<T> {
  const query = withSupportQuery(options.query, options.support);
  const response = await fetch(`${getApiUrl(path)}${buildQueryString(query)}`, {
    method: options.method ?? "GET",
    credentials: "include",
    cache: "no-store",
    headers: edgeHeaders(options.support),
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });
  if (!response.ok) {
    const raw = await response.json().catch(() => null);
    throw new EdgeRequestError(
      response.status,
      normaliseErrorBody(response.status, raw, parseRetryAfter(response.headers.get("Retry-After"))),
    );
  }
  return (await response.json()) as T;
}

/** Maps a section 7.11 error code to the Console state shown for it. */
export function stateForCode(code: string | undefined, param?: string): EdgeDataState {
  switch (code) {
    case "EDGE_OFFLINE":
    case "LEDGER_UNAVAILABLE":
      return "offline";
    case "EDGE_TIMEOUT":
    case "DEADLINE_EXCEEDED":
    case "STREAM_STALLED":
      return "timeout";
    case "EDGE_BUSY":
      return "busy";
    case "TENANT_MISMATCH":
      return "tenant-error";
    case "SUPPORT_ACCESS_DISABLED":
      return "support-disabled";
    case "NOT_FOUND":
      return "not-found";
    case "INVALID_PARAMS":
      return param === "/pspId" || param === "/supportReason" ? "support-required" : "error";
    default:
      return "error";
  }
}

/** The Console state of a thrown error (an {@link EdgeRequestError} or anything else). */
export function stateForError(error: unknown): EdgeDataState {
  if (error instanceof EdgeRequestError) return stateForCode(error.body.code, error.body.param);
  return "error";
}

/** Envelope fields that report completeness (list, batchGet, related). */
export interface CompletenessFields {
  incomplete?: boolean;
  unavailableSites?: EdgeUnavailableSite[];
}

/** {@code partial} when some site did not answer or the page is incomplete, else {@code ready}. */
export function stateForEnvelope(data: CompletenessFields | null | undefined): EdgeDataState {
  if (!data) return "ready";
  return data.incomplete || (data.unavailableSites?.length ?? 0) > 0 ? "partial" : "ready";
}

/**
 * The Console state of a workflow view's {@code transactionDetailsStatus} (alert detail, SAR
 * detail, case timeline and network), which reports the pull without failing the view.
 */
export function stateForPullStatus(status: EdgePullStatus | null | undefined): EdgeDataState {
  if (!status) return "ready";
  switch (status.state) {
    case "OK":
    case "NONE":
    case "NOT_REQUESTED":
      return "ready";
    case "PARTIAL":
      return "partial";
    default:
      return stateForCode(status.state, status.error?.param);
  }
}

/** The newest {@code lastSeenAt} among an error body and unavailable sites, if any. */
export function lastSeenOf(error?: EdgeDataError | null, sites?: EdgeUnavailableSite[] | null): string | undefined {
  const candidates = [error?.lastSeenAt, ...(sites ?? []).map((site) => site.lastSeenAt)].filter(
    (value): value is string => typeof value === "string" && value.length > 0,
  );
  if (!candidates.length) return undefined;
  return candidates.reduce((latest, value) => (Date.parse(value) > Date.parse(latest) ? value : latest));
}

/** Validation message for a support reason, or null when acceptable. */
export function supportReasonError(reason: string): string | null {
  const length = reason.trim().length;
  if (length < SUPPORT_REASON_MIN) return `Give a reason of at least ${SUPPORT_REASON_MIN} characters.`;
  if (length > SUPPORT_REASON_MAX) return `Keep the reason under ${SUPPORT_REASON_MAX} characters.`;
  return null;
}

/** Human label of an unavailable site's error code. */
export function siteCodeLabel(code: string): string {
  switch (code) {
    case "EDGE_OFFLINE":
      return "offline";
    case "EDGE_TIMEOUT":
    case "DEADLINE_EXCEEDED":
      return "timed out";
    case "EDGE_BUSY":
      return "busy";
    case "LEDGER_UNAVAILABLE":
      return "ledger unavailable";
    default:
      return code.toLowerCase().replace(/_/g, " ");
  }
}
