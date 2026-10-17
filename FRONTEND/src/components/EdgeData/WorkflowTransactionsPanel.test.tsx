/** @vitest-environment happy-dom */
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import WorkflowTransactionsPanel from "./WorkflowTransactionsPanel";
import EdgeOfflineBanner from "./EdgeOfflineBanner";

/** Renders inside a router (rows link to record pages). */
function renderIn(node: React.ReactNode) {
  return render(<MemoryRouter>{node}</MemoryRouter>);
}

describe("workflow transaction panel and offline banner", () => {
  afterEach(cleanup);

  it("shows the pulled rows with a partial warning naming the missing site", () => {
    renderIn(
      <WorkflowTransactionsPanel
        details={[{ id: "e1", edgeAssessmentId: "e1", txnRef: "REF-9", decision: "BLOCK", amountCents: 50000, currency: "USD" }]}
        status={{ source: "EDGE", state: "PARTIAL", incomplete: true, missing: ["x"],
          unavailableSites: [{ siteId: "mombasa", code: "EDGE_TIMEOUT" }] }}
      />,
    );
    expect(screen.getByText("REF-9")).toBeTruthy();
    expect(screen.getByLabelText("Unavailable Edge sites").textContent).toContain("mombasa: timed out");
    expect(screen.getByText(/Not held by any answering site: 1 reference/)).toBeTruthy();
  });

  it("keeps the cloud note and hides the table when the Edge is offline", () => {
    renderIn(
      <WorkflowTransactionsPanel
        details={null}
        cloudNote="The alert's own fields stay available."
        status={{ state: "EDGE_OFFLINE", incomplete: true, missing: [], unavailableSites: [],
          error: { code: "EDGE_OFFLINE", httpStatus: 503, message: "", retryable: true, lastSeenAt: "2026-10-17T04:00:00Z" } }}
      />,
    );
    expect(screen.getByText("The PSP's Edge is offline")).toBeTruthy();
    expect(screen.getByText("The alert's own fields stay available.")).toBeTruthy();
    expect(screen.getByText(/Last seen:/).textContent).toContain(new Date("2026-10-17T04:00:00Z").toLocaleString());
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("says when nothing is referenced", () => {
    renderIn(<WorkflowTransactionsPanel status={{ state: "NONE", incomplete: false, missing: [], unavailableSites: [] }} />);
    expect(screen.getByText("No transaction is referenced.")).toBeTruthy();
  });

  it("renders nothing for a complete answer", () => {
    const { container } = render(<EdgeOfflineBanner state="ready" />);
    expect(container.innerHTML).toBe("");
  });
});
