/** @vitest-environment happy-dom */
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { useEdgeData } from "./useEdgeData";
import { EdgeSupportContext, type EdgeSupportValue } from "../contexts/edgeSupport";
import EdgeOfflineBanner from "../components/EdgeData/EdgeOfflineBanner";
import { EDGE_DATA_GC_TIME_MS } from "../lib/edgeData";
import type { EdgeDataEnvelope, Transaction } from "../types";

const fetchMock = vi.fn();

/** A JSON response with optional headers. */
function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", ...headers } });
}

/** Renders the hook's state, the banner and the item count. */
function Probe() {
  const result = useEdgeData<EdgeDataEnvelope<Transaction>>({ key: ["probe"], path: "transactions", query: { size: 25 } });
  return (
    <div>
      <span data-testid="state">{result.state}</span>
      <span data-testid="items">{result.data?.items.length ?? 0}</span>
      <span data-testid="retry">{result.retryAfterMs ?? ""}</span>
      <EdgeOfflineBanner
        state={result.state}
        error={result.error}
        lastSeenAt={result.lastSeenAt}
        unavailableSites={result.unavailableSites}
        retryAfterMs={result.retryAfterMs}
      />
    </div>
  );
}

let client: QueryClient;

/** Wraps in a fresh query client and the given support context. */
function renderProbe(support?: Partial<EdgeSupportValue>) {
  client = new QueryClient();
  const value: EdgeSupportValue = {
    isPlatform: false,
    session: null,
    ready: true,
    params: null,
    start: () => undefined,
    end: () => undefined,
    ...support,
  };
  const wrap = (node: ReactNode) => (
    <QueryClientProvider client={client}>
      <EdgeSupportContext.Provider value={value}>{node}</EdgeSupportContext.Provider>
    </QueryClientProvider>
  );
  return render(wrap(<Probe />));
}

describe("useEdgeData", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("is ready with a complete envelope and never touches browser storage", async () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem");
    fetchMock.mockResolvedValue(json(200, { items: [{ id: "a" }, { id: "b" }], nextCursor: null, incomplete: false, unavailableSites: [], source: "EDGE" }));
    renderProbe();
    expect(screen.getByTestId("state").textContent).toBe("loading");
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("ready"));
    expect(screen.getByTestId("items").textContent).toBe("2");
    expect(screen.queryByTestId("edge-banner")).toBeNull();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/transactions?size=25");
    expect(init.cache).toBe("no-store");
    expect(setItem).not.toHaveBeenCalled();
    const cached = client.getQueryCache().findAll({ queryKey: ["edge-data"] });
    expect(cached).toHaveLength(1);
    expect(cached[0].options.gcTime).toBe(EDGE_DATA_GC_TIME_MS);
    setItem.mockRestore();
  });

  it("is partial and names the sites that did not answer", async () => {
    fetchMock.mockResolvedValue(json(200, {
      items: [{ id: "a" }], nextCursor: "c", incomplete: true, source: "EDGE",
      unavailableSites: [{ siteId: "nairobi-2", code: "EDGE_OFFLINE", lastSeenAt: "2026-10-17T08:00:00Z" }],
    }));
    renderProbe();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("partial"));
    expect(screen.getByText(/some Edge sites did not answer/)).toBeTruthy();
    expect(screen.getByLabelText("Unavailable Edge sites").textContent).toContain("nairobi-2");
  });

  it("is offline with the last-seen time on 503 EDGE_OFFLINE", async () => {
    fetchMock.mockResolvedValue(json(503, { code: "EDGE_OFFLINE", httpStatus: 503, message: "offline", retryable: true, lastSeenAt: "2026-10-17T07:30:00Z" }));
    renderProbe();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("offline"));
    expect(screen.getByText("The PSP's Edge is offline")).toBeTruthy();
    expect(screen.getByText(/Last seen:/).textContent).toContain(new Date("2026-10-17T07:30:00Z").toLocaleString());
  });

  it("is timeout on 504", async () => {
    fetchMock.mockResolvedValue(json(504, { code: "EDGE_TIMEOUT", httpStatus: 504, message: "t", retryable: true }));
    renderProbe();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("timeout"));
  });

  it("is busy on 429 and reads Retry-After", async () => {
    fetchMock.mockResolvedValue(json(429, { code: "EDGE_BUSY", httpStatus: 429, message: "b", retryable: true }, { "Retry-After": "3" }));
    renderProbe();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("busy"));
    expect(screen.getByTestId("retry").textContent).toBe("3000");
    expect(screen.getByText(/Try again in 3 s/)).toBeTruthy();
  });

  it("is tenant-error on 403 TENANT_MISMATCH", async () => {
    fetchMock.mockResolvedValue(json(403, { code: "TENANT_MISMATCH", httpStatus: 403, message: "m", retryable: false }));
    renderProbe();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("tenant-error"));
    expect(screen.getByText("This data belongs to another PSP")).toBeTruthy();
  });

  it("explains a 403 when the PSP has not enabled support access", async () => {
    fetchMock.mockResolvedValue(json(403, { code: "SUPPORT_ACCESS_DISABLED", httpStatus: 403, message: "d", retryable: false }));
    renderProbe({ isPlatform: true, ready: true, params: { pspId: 9, reason: "Ticket 77 duplicate charge" } });
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("support-disabled"));
    expect(screen.getByText("Support access is not enabled by this PSP")).toBeTruthy();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("pspId=9");
    expect(init.headers["X-Support-Reason"]).toBe("Ticket 77 duplicate charge");
  });

  it("holds a platform admin's pull until a support session is open", () => {
    renderProbe({ isPlatform: true, ready: false, params: null });
    expect(screen.getByTestId("state").textContent).toBe("support-required");
    expect(fetchMock).not.toHaveBeenCalled();
    expect(screen.getByText("A support reason is needed")).toBeTruthy();
  });
});
