import { describe, expect, it } from "vitest";
import {
  buildQueryString,
  lastSeenOf,
  normaliseErrorBody,
  parseRetryAfter,
  stateForCode,
  stateForEnvelope,
  stateForPullStatus,
  supportReasonError,
  withSupportQuery,
} from "./edgeData";
import { FIRST_PAGE, currentCursor, cursorPagerReducer, pageIndex } from "./cursorPager";
import { exportCompleteness } from "./edgeExport";

/** Header lookup over a plain object. */
const headers = (h: Record<string, string>) => ({ get: (name: string) => h[name] ?? null });

describe("edge data states", () => {
  it("maps contract 7.11 codes to Console states", () => {
    expect(stateForCode("EDGE_OFFLINE")).toBe("offline");
    expect(stateForCode("LEDGER_UNAVAILABLE")).toBe("offline");
    expect(stateForCode("EDGE_TIMEOUT")).toBe("timeout");
    expect(stateForCode("DEADLINE_EXCEEDED")).toBe("timeout");
    expect(stateForCode("EDGE_BUSY")).toBe("busy");
    expect(stateForCode("TENANT_MISMATCH")).toBe("tenant-error");
    expect(stateForCode("SUPPORT_ACCESS_DISABLED")).toBe("support-disabled");
    expect(stateForCode("INVALID_PARAMS", "/pspId")).toBe("support-required");
    expect(stateForCode("INVALID_PARAMS", "/page")).toBe("error");
  });

  it("reports partial when a site did not answer", () => {
    expect(stateForEnvelope({ incomplete: false, unavailableSites: [] })).toBe("ready");
    expect(stateForEnvelope({ incomplete: true, unavailableSites: [] })).toBe("partial");
    expect(stateForEnvelope({ incomplete: false, unavailableSites: [{ siteId: "s1", code: "EDGE_OFFLINE" }] })).toBe("partial");
  });

  it("reads a workflow view's pull status", () => {
    expect(stateForPullStatus({ state: "OK", incomplete: false, unavailableSites: [], missing: [] })).toBe("ready");
    expect(stateForPullStatus({ state: "NONE", incomplete: false, unavailableSites: [], missing: [] })).toBe("ready");
    expect(stateForPullStatus({ state: "PARTIAL", incomplete: true, unavailableSites: [], missing: [] })).toBe("partial");
    expect(stateForPullStatus({ state: "EDGE_OFFLINE", incomplete: true, unavailableSites: [], missing: [] })).toBe("offline");
  });

  it("synthesises an error body from a bare status and Retry-After", () => {
    const body = normaliseErrorBody(429, null, parseRetryAfter("7"));
    expect(body.code).toBe("EDGE_BUSY");
    expect(body.retryAfterMs).toBe(7000);
    expect(body.retryable).toBe(true);
    expect(normaliseErrorBody(503, { code: "EDGE_OFFLINE", message: "x", lastSeenAt: "2026-10-17T10:00:00Z" }).lastSeenAt)
      .toBe("2026-10-17T10:00:00Z");
  });

  it("picks the newest last-seen instant", () => {
    expect(lastSeenOf({ code: "EDGE_OFFLINE", httpStatus: 503, message: "", retryable: true, lastSeenAt: "2026-10-17T09:00:00Z" },
      [{ siteId: "a", code: "EDGE_OFFLINE", lastSeenAt: "2026-10-17T10:00:00Z" }])).toBe("2026-10-17T10:00:00Z");
    expect(lastSeenOf(undefined, [])).toBeUndefined();
  });

  it("builds repeated query keys and adds the supported PSP", () => {
    expect(buildQueryString({ decisions: ["BLOCK", "HOLD"], cursor: undefined, size: 50 })).toBe("?decisions=BLOCK&decisions=HOLD&size=50");
    expect(withSupportQuery({ size: 10 }, { pspId: 7, reason: "Ticket 123 check" })).toEqual({ size: 10, pspId: 7 });
    expect(withSupportQuery({ size: 10 }, null)).toEqual({ size: 10 });
  });

  it("validates the support reason length", () => {
    expect(supportReasonError("short")).toMatch(/at least 10/);
    expect(supportReasonError("Ticket 4411: verify duplicate charge")).toBeNull();
    expect(supportReasonError("x".repeat(501))).toMatch(/under 500/);
  });
});

describe("cursor paging", () => {
  it("walks forward with cursors and back to the first page", () => {
    let state = FIRST_PAGE;
    expect(currentCursor(state)).toBeUndefined();
    state = cursorPagerReducer(state, { type: "next", cursor: "c1" });
    state = cursorPagerReducer(state, { type: "next", cursor: "c2" });
    expect(pageIndex(state)).toBe(2);
    expect(currentCursor(state)).toBe("c2");
    state = cursorPagerReducer(state, { type: "previous" });
    expect(currentCursor(state)).toBe("c1");
    state = cursorPagerReducer(state, { type: "reset" });
    expect(pageIndex(state)).toBe(0);
    expect(cursorPagerReducer(state, { type: "previous" })).toBe(state);
  });
});

describe("streamed export completeness", () => {
  it("reads the open-time headers", () => {
    const c = exportCompleteness("csv", headers({ "X-Edge-Incomplete": "true", "X-Edge-Unavailable-Sites": "site-b (EDGE_OFFLINE)" }), "a,b\r\n1,2\r\n");
    expect(c).toEqual({ incomplete: true, unavailableSites: "site-b (EDGE_OFFLINE)", rows: null });
  });

  it("reads the NDJSON summary line", () => {
    const tail = '{"id":"1"}\n{"@summary":{"rows":1,"incomplete":true,"unavailableSites":[{"siteId":"s2","code":"EDGE_TIMEOUT"}]}}\n';
    expect(exportCompleteness("ndjson", headers({ "X-Edge-Incomplete": "false" }), tail))
      .toEqual({ incomplete: true, unavailableSites: "s2 (EDGE_TIMEOUT)", rows: 1 });
    expect(exportCompleteness("ndjson", headers({}), '{"@summary":{"rows":3,"incomplete":false}}\n').incomplete).toBe(false);
    expect(exportCompleteness("ndjson", headers({}), '{"id":"1"}\n').incomplete).toBe(true);
  });

  it("reads the CSV incomplete trailer", () => {
    const c = exportCompleteness("csv", headers({}), "a\r\n1\r\n# INCOMPLETE EXPORT: 1 rows; unavailable sites: s3 (EDGE_BUSY)\r\n");
    expect(c.incomplete).toBe(true);
    expect(c.unavailableSites).toBe("1 rows; unavailable sites: s3 (EDGE_BUSY)");
  });
});
