/** @vitest-environment happy-dom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TransactionMonitoringLive from "./TransactionMonitoringLive";

vi.mock("../../features/api/decisionStats", () => ({
  useDecisionStatsSummary: () => ({
    data: {
      today: { records: 12, flagged: 3, block: 1, allow: 9, alert: 1, hold: 1, failClosed: 0, ctrRequired: 0, sanctionsHit: 0, averageScore: 20 },
      last24h: { records: 20, flagged: 4, block: 1, allow: 16, alert: 2, hold: 1, failClosed: 0, ctrRequired: 0, sanctionsHit: 0, averageScore: 20 },
      highScore24h: 2,
    },
    isLoading: false,
  }),
  useRecentDecisionRecords: () => ({
    data: { items: [{ edgeAssessmentId: "rec-1", txnRef: "REF-1", pspId: 4, decision: "BLOCK", score: 90 }] },
    isLoading: false,
    isError: false,
  }),
}));

const fetchMock = vi.fn();

/** A page of the transactions envelope. */
function page(ids: string[], nextCursor: string | null) {
  return new Response(JSON.stringify({
    items: ids.map((id) => ({ id, edgeAssessmentId: id, txnRef: `REF-${id}`, merchantId: "M1", amountCents: 1250, currency: "KES", decision: "ALLOW", txnTs: "2026-10-17T09:00:00Z", siteId: "site-a", source: "EDGE" })),
    nextCursor,
    incomplete: false,
    unavailableSites: [],
    source: "EDGE",
  }), { status: 200, headers: { "Content-Type": "application/json" } });
}

/** Transaction list URLs requested so far. */
const listCalls = () => fetchMock.mock.calls.map((c) => String(c[0])).filter((u) => u.startsWith("/api/v1/transactions?"));

describe("Live Monitoring transaction list", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockImplementation((url: string) => {
      if (url.includes("cursor=c1")) return Promise.resolve(page(["t3"], null));
      return Promise.resolve(page(["t1", "t2"], "c1"));
    });
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("reads the items envelope and pages by cursor only", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter initialEntries={["/transaction-monitoring/live"]}>
          <TransactionMonitoringLive />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText("REF-t1");
    expect(screen.getByText("REF-t2")).toBeTruthy();
    expect(screen.getByTestId("cursor-range").textContent).toContain("Page 1");
    const first = listCalls()[0];
    expect(first).toContain("from=");
    expect(first).toContain("to=");
    expect(first).not.toContain("cursor=");

    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    await screen.findByText("REF-t3");
    expect(screen.getByTestId("cursor-range").textContent).toContain("Page 2");
    expect((screen.getByRole("button", { name: "Next page" }) as HTMLButtonElement).disabled).toBe(true);
    const second = listCalls().find((u) => u.includes("cursor=c1"))!;
    // The second page keeps the exact window its cursor was minted for.
    const params = (u: string) => new URLSearchParams(u.split("?")[1]);
    expect(params(second).get("from")).toBe(params(first).get("from"));
    expect(params(second).get("to")).toBe(params(first).get("to"));

    fireEvent.click(screen.getByRole("button", { name: "Previous page" }));
    await screen.findByText("REF-t1");
    expect(screen.getByTestId("cursor-range").textContent).toContain("Page 1");
    expect(listCalls().every((u) => !/[?&]page=/.test(u))).toBe(true);
  });

  it("shows the offline banner and keeps the cloud decision records visible", async () => {
    fetchMock.mockImplementation(() => Promise.resolve(new Response(JSON.stringify({
      code: "EDGE_OFFLINE", httpStatus: 503, message: "no session", retryable: true, lastSeenAt: "2026-10-17T05:00:00Z",
    }), { status: 503, headers: { "Content-Type": "application/json" } })));
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <TransactionMonitoringLive />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText("The PSP's Edge is offline");
    expect(screen.getByText("REF-1")).toBeTruthy();
    expect(screen.getByText("Evaluated today")).toBeTruthy();
  });

  it("restarts at the first page when a filter changes", async () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <TransactionMonitoringLive />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await screen.findByText("REF-t1");
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    await screen.findByText("REF-t3");
    fireEvent.change(screen.getByLabelText("Decision"), { target: { value: "BLOCK" } });
    await waitFor(() => expect(listCalls().some((u) => u.includes("decisions=BLOCK"))).toBe(true));
    const filtered = listCalls().filter((u) => u.includes("decisions=BLOCK"));
    expect(filtered.every((u) => !u.includes("cursor="))).toBe(true);
    expect(screen.getByTestId("cursor-range").textContent).toContain("Page 1");
  });
});
