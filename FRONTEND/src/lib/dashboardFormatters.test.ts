import { describe, expect, it } from "vitest";
import { alertHeadline, alertSubline, caseStatusChipClass } from "./dashboardFormatters";
import type { Alert } from "../types";

describe("dashboardFormatters", () => {
  it("uses alert type instead of bare id in subline", () => {
    const alert: Alert = {
      id: 1,
      alertType: "VELOCITY_SPIKE",
      priority: "HIGH",
      status: "OPEN",
      description: "",
      createdAt: new Date().toISOString(),
    };
    expect(alertHeadline(alert)).toBe("VELOCITY SPIKE");
    expect(alertSubline(alert)).toBe("No summary on file");
  });

  it("maps investigating case status to info chip (not gold accent)", () => {
    expect(caseStatusChipClass("INVESTIGATING")).toContain("db-chip--info");
  });
});
