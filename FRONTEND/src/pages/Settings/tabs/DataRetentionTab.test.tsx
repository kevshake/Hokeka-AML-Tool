/** @vitest-environment happy-dom */
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DataSettings } from "../../../features/api/dataResidency";
import DataRetentionTab from "./DataRetentionTab";
import SupportAccessTab from "./SupportAccessTab";

const proposeRetention = vi.fn();
const setSupport = vi.fn();
let currentUser: { username: string; pspId: number; role: { name: string } } = {
  username: "alice",
  pspId: 42,
  role: { name: "PSP_ADMIN" },
};
let settings: DataSettings;

vi.mock("../../../contexts/AuthContext", () => ({
  useAuth: () => ({ user: currentUser }),
}));

vi.mock("../../../features/api/dataResidency", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../../features/api/dataResidency")>();
  return {
    ...actual,
    useDataSettings: () => ({ data: settings, isLoading: false, isError: false }),
    useProposeRetention: () => ({ mutateAsync: proposeRetention, isPending: false }),
    useSetSupportAccess: () => ({ mutateAsync: setSupport, isPending: false }),
    useSetCrossPspOptIn: () => ({ mutateAsync: vi.fn(), isPending: false }),
    useReviewDataSettingChange: () => ({ mutateAsync: vi.fn(), isPending: false }),
  };
});

function renderWithClient(node: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

describe("Settings → Data Retention", () => {
  afterEach(cleanup);

  beforeEach(() => {
    proposeRetention.mockReset().mockResolvedValue({ status: "PENDING_APPROVAL" });
    setSupport.mockReset().mockResolvedValue({ status: "APPLIED" });
    currentUser = { username: "alice", pspId: 42, role: { name: "PSP_ADMIN" } };
    settings = {
      pspId: 42,
      retentionYears: 10,
      retentionEffectiveSince: "2026-01-01T00:00:00Z",
      pendingRetention: null,
      supportAccessEnabled: true,
      supportAccessChangedAt: "2026-10-01T00:00:00Z",
      crossPspOptIn: false,
      crossPspOptInChangedAt: "2026-10-01T00:00:00Z",
      minRetentionYears: 5,
      maxRetentionYears: 25,
      reductionDelayDays: 30,
      openChanges: [],
    };
  });

  it("shows the period in force and warns that a shortening waits 30 days", () => {
    renderWithClient(<DataRetentionTab pspId={42} />);
    expect(screen.getByTestId("retention-in-force").textContent).toBe("10 years");
    fireEvent.change(screen.getByLabelText("Years"), { target: { value: "7" } });
    expect(screen.getByText(/30 days after approval/)).toBeTruthy();
  });

  it("refuses a period below the floor before calling the server", () => {
    renderWithClient(<DataRetentionTab pspId={42} />);
    fireEvent.change(screen.getByLabelText("Years"), { target: { value: "4" } });
    expect(screen.getByText(/cannot be shorter than 5 years/)).toBeTruthy();
    const button = screen.getByRole("button", { name: "Propose change" }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  it("proposes a change with its reason", async () => {
    renderWithClient(<DataRetentionTab pspId={42} />);
    fireEvent.change(screen.getByLabelText("Years"), { target: { value: "12" } });
    fireEvent.change(screen.getByLabelText("Reason (recorded in the audit log)"), { target: { value: "Regulator request" } });
    fireEvent.click(screen.getByRole("button", { name: "Propose change" }));
    await screen.findByText(/second reviewer approves/);
    expect(proposeRetention).toHaveBeenCalledWith({ retentionYears: 12, reason: "Regulator request" });
  });

  it("shows an approved shortening that is not yet in force", () => {
    settings = {
      ...settings,
      pendingRetention: { years: 7, approvedAt: "2026-10-16T08:00:00Z", effectiveAt: "2026-11-15T08:00:00Z" },
    };
    renderWithClient(<DataRetentionTab pspId={42} />);
    expect(screen.getByTestId("retention-pending").textContent).toMatch(/Shortening to 7 years/);
  });

  it("hides the approve button from the maker and shows it to another reviewer", () => {
    settings = {
      ...settings,
      openChanges: [{
        id: 3, setting: "RETENTION_YEARS", oldValue: "10", newValue: "12", status: "PENDING_APPROVAL",
        reason: "Longer", requestedBy: "alice", requestedAt: "2026-10-16T08:00:00Z", reviewedBy: null,
        reviewedAt: null, reviewReason: null, effectiveAt: null, appliedAt: null,
      }],
    };
    const { unmount } = renderWithClient(<DataRetentionTab pspId={42} />);
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
    unmount();
    currentUser = { username: "bob", pspId: 42, role: { name: "MLRO" } };
    renderWithClient(<DataRetentionTab pspId={42} />);
    expect(screen.getByRole("button", { name: "Approve" })).toBeTruthy();
    expect(screen.getByText(/Only a PSP administrator can propose/)).toBeTruthy();
  });

  it("withdraws support access immediately", async () => {
    renderWithClient(<SupportAccessTab pspId={42} />);
    const reasons = screen.getAllByLabelText("Reason (recorded in the audit log)");
    fireEvent.change(reasons[0], { target: { value: "Ticket closed" } });
    fireEvent.click(screen.getByRole("button", { name: "Withdraw now" }));
    await screen.findByText(/in force and is sent to your Edge/);
    expect(setSupport).toHaveBeenCalledWith({ enabled: false, reason: "Ticket closed" });
  });
});
