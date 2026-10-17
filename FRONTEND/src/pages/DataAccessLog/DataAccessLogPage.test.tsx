/** @vitest-environment happy-dom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DataAccessLogPage from "./DataAccessLogPage";

let currentUser: { username: string; pspId: number; role: { name: string } } = {
  username: "alice",
  pspId: 4,
  role: { name: "PSP_ADMIN" },
};

vi.mock("../../contexts/AuthContext", () => ({
  useAuth: () => ({ user: currentUser }),
}));

vi.mock("../../features/api/queries", () => ({
  useAllPsps: () => ({ data: [{ pspId: 4, legalName: "Acme Pay" }] }),
}));

vi.mock("../../components/Layout/HokekaPageShell", () => ({
  default: ({ children, title }: { children: React.ReactNode; title: string }) => <main><h1>{title}</h1>{children}</main>,
}));

const fetchMock = vi.fn();

/** One page of the access log with a PSP pull and a support pull. */
function logPage(number: number) {
  return new Response(JSON.stringify({
    content: [
      {
        auditId: `a-${number}-1`, requestedAt: "2026-10-17T08:00:00Z", completedAt: "2026-10-17T08:00:01Z",
        actorKind: "PSP_USER", actorUserId: "alice", actorRole: "PSP_ADMIN", purpose: "TRANSACTION_MONITORING",
        supportAccess: false, op: "txn.search", paramsSummary: { decisions: ["BLOCK"] }, paramsDigest: "d",
        sitesContacted: ["s1"], sitesAnswered: ["s1"], itemCount: 25, bytes: 1000, outcome: "OK",
      },
      {
        auditId: `a-${number}-2`, requestedAt: "2026-10-17T09:00:00Z", completedAt: "2026-10-17T09:00:01Z",
        actorKind: "PLATFORM_USER", actorUserId: "ops", actorRole: "SUPER_ADMIN", purpose: "SUPPORT_ACCESS",
        supportAccess: true, supportReason: "Ticket 5521: missing settlement", op: "txn.get", paramsSummary: { idCount: 1 },
        paramsDigest: "e", sitesContacted: ["s1", "s2"], sitesAnswered: ["s1"], itemCount: 1, bytes: 200, outcome: "PARTIAL",
      },
    ],
    totalElements: 120, totalPages: 3, size: 50, number, pspId: 4,
  }), { status: 200, headers: { "Content-Type": "application/json" } });
}

/** Access-log page URLs requested so far. */
const logCalls = () => fetchMock.mock.calls.map((c) => String(c[0])).filter((u) => u.includes("edge-data/access-log?"));

/** Renders the page at /data-access-log. */
function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/data-access-log"]}>
        <DataAccessLogPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("Data access log", () => {
  beforeEach(() => {
    currentUser = { username: "alice", pspId: 4, role: { name: "PSP_ADMIN" } };
    fetchMock.mockReset();
    fetchMock.mockImplementation((url: string) => {
      if (url.includes("/export")) {
        return Promise.resolve(new Response("auditId,op\r\na,txn.search\r\n", {
          status: 200,
          headers: { "Content-Type": "text/csv", "Content-Disposition": 'attachment; filename="edge-data-access-log-4.csv"' },
        }));
      }
      const page = Number(new URLSearchParams(url.split("?")[1]).get("page") ?? 0);
      return Promise.resolve(logPage(page));
    });
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("URL", Object.assign(URL, { createObjectURL: vi.fn(() => "blob:x"), revokeObjectURL: vi.fn() }));
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("pages on the server and highlights support pulls with their reason", async () => {
    renderPage();
    await screen.findByText(/Ticket 5521: missing settlement/);
    expect(screen.getByTestId("support-row")).toBeTruthy();
    expect(screen.getAllByTestId("access-row")).toHaveLength(1);
    expect(screen.getByText("120 pulls")).toBeTruthy();
    const first = new URLSearchParams(logCalls()[0].split("?")[1]);
    expect(first.get("page")).toBe("0");
    expect(first.get("size")).toBe("50");
    expect(first.get("pspId")).toBeNull();
  });

  it("sends filters to the server and resets to the first page", async () => {
    renderPage();
    await screen.findByText(/Ticket 5521/);
    fireEvent.change(screen.getByLabelText("Support access"), { target: { value: "true" } });
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "PARTIAL" } });
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-10-01" } });
    await waitFor(() => {
      const calls = logCalls();
      const last = new URLSearchParams(calls[calls.length - 1].split("?")[1]);
      expect(last.get("supportAccess")).toBe("true");
      expect(last.get("outcome")).toBe("PARTIAL");
      expect(last.get("from")).toBe(new Date("2026-10-01T00:00:00").toISOString());
      expect(last.get("page")).toBe("0");
    });
  });

  it("exports the filtered log as a streamed file", async () => {
    renderPage();
    await screen.findByText(/Ticket 5521/);
    fireEvent.change(screen.getByLabelText("Operation"), { target: { value: "txn.get" } });
    fireEvent.click(screen.getByRole("button", { name: /Export CSV/ }));
    await waitFor(() => expect(fetchMock.mock.calls.some((c) => String(c[0]).includes("edge-data/access-log/export?"))).toBe(true));
    const url = fetchMock.mock.calls.map((c) => String(c[0])).find((u) => u.includes("/export?"))!;
    expect(url).toContain("op=txn.get");
    expect(url).toContain("format=csv");
    expect(screen.queryByTestId("export-incomplete")).toBeNull();
  });

  it("asks platform staff to choose the PSP first", async () => {
    currentUser = { username: "ops", pspId: 0, role: { name: "SUPER_ADMIN" } };
    renderPage();
    expect(screen.getByText("Choose a PSP to read its data access log.")).toBeTruthy();
    expect(logCalls()).toHaveLength(0);
    fireEvent.change(screen.getByLabelText("PSP"), { target: { value: "4" } });
    await screen.findByText(/Ticket 5521/);
    expect(new URLSearchParams(logCalls()[0].split("?")[1]).get("pspId")).toBe("4");
  });
});
