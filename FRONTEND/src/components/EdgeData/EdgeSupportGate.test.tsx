/** @vitest-environment happy-dom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import EdgeSupportGate from "./EdgeSupportGate";
import EdgeOfflineBanner from "./EdgeOfflineBanner";
import { EdgeSupportProvider } from "../../contexts/EdgeSupportContext";
import { useEdgeData } from "../../hooks/useEdgeData";

let currentUser: { username: string; pspId: number; role: { name: string } } = {
  username: "ops",
  pspId: 0,
  role: { name: "SUPER_ADMIN" },
};

vi.mock("../../contexts/AuthContext", () => ({
  useAuth: () => ({ user: currentUser }),
}));

vi.mock("../../features/api/queries", () => ({
  useAllPsps: () => ({ data: [{ pspId: 7, legalName: "Acme Pay" }, { pspId: 8, legalName: "Beta Wallet" }] }),
}));

const fetchMock = vi.fn();

/** A page that pulls transactions through the hook and shows its state. */
function Pull() {
  const r = useEdgeData<{ items: unknown[] }>({ key: ["support-probe"], path: "transactions" });
  return (
    <>
      <span data-testid="state">{r.state}</span>
      <EdgeOfflineBanner state={r.state} error={r.error} />
    </>
  );
}

/** Provider, gate and a pulling page. */
function renderFlow() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <EdgeSupportProvider>
        <EdgeSupportGate />
        <Pull />
      </EdgeSupportProvider>
    </QueryClientProvider>,
  );
}

describe("platform-admin support flow", () => {
  beforeEach(() => {
    currentUser = { username: "ops", pspId: 0, role: { name: "SUPER_ADMIN" } };
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("requires a PSP and a reason before any pull, then sends both", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ items: [], nextCursor: null, incomplete: false, unavailableSites: [], source: "EDGE" }), { status: 200 }));
    renderFlow();
    expect(screen.getByTestId("state").textContent).toBe("support-required");
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Open support session" }));
    expect(screen.getByText("Choose the PSP you are supporting.")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("PSP"), { target: { value: "7" } });
    fireEvent.change(screen.getByLabelText("Support reason"), { target: { value: "too short" } });
    fireEvent.click(screen.getByRole("button", { name: "Open support session" }));
    expect(screen.getByText(/at least 10 characters/)).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Support reason"), { target: { value: "Ticket 5521: missing settlement" } });
    fireEvent.click(screen.getByRole("button", { name: "Open support session" }));
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("ready"));
    expect(screen.getByTestId("support-session").textContent).toContain("Acme Pay");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain("pspId=7");
    expect(init.headers["X-Support-Reason"]).toBe("Ticket 5521: missing settlement");

    fireEvent.click(screen.getByRole("button", { name: /End support session/ }));
    expect(screen.getByTestId("state").textContent).toBe("support-required");
  });

  it("shows a clear message when the PSP has not enabled support access", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({
      code: "SUPPORT_ACCESS_DISABLED", httpStatus: 403, message: "support access is not enabled", retryable: false,
    }), { status: 403 }));
    renderFlow();
    fireEvent.change(screen.getByLabelText("PSP"), { target: { value: "8" } });
    fireEvent.change(screen.getByLabelText("Support reason"), { target: { value: "Ticket 9001: reconcile chargeback" } });
    fireEvent.click(screen.getByRole("button", { name: "Open support session" }));
    await screen.findByText("Support access is not enabled by this PSP");
    expect(screen.getByText(/Settings → Support & Data Sharing/)).toBeTruthy();
  });

  it("asks nothing of PSP users", async () => {
    currentUser = { username: "alice", pspId: 7, role: { name: "PSP_ADMIN" } };
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ items: [], nextCursor: null, incomplete: false, unavailableSites: [], source: "EDGE" }), { status: 200 }));
    renderFlow();
    expect(screen.queryByTestId("support-gate")).toBeNull();
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("ready"));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).not.toContain("pspId=");
    expect(init.headers["X-Support-Reason"]).toBeUndefined();
  });
});
