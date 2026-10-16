import { describe, expect, it } from "vitest";
import {
  canProposeDataSettings,
  canReviewChange,
  canViewDataSettings,
  describeValue,
  retentionChangeNotice,
  retentionError,
} from "./dataResidency";
import { getSettingsTabsForUser } from "./settingsTabs";

const pspAdmin = { pspId: 42, role: { name: "PSP_ADMIN" }, username: "alice" };
const mlro = { pspId: 42, role: { name: "MLRO" }, username: "bob" };
const analyst = { pspId: 42, role: { name: "PSP_ANALYST" }, username: "carol" };
const platform = { pspId: 0, role: { name: "SUPER_ADMIN" }, username: "root" };

describe("data residency role gating", () => {
  it("shows the tabs to PSP admins and reviewers only", () => {
    expect(canViewDataSettings(pspAdmin)).toBe(true);
    expect(canViewDataSettings(mlro)).toBe(true);
    expect(canViewDataSettings(analyst)).toBe(false);
    expect(canViewDataSettings(platform)).toBe(false);
    const ids = getSettingsTabsForUser(pspAdmin).map((t) => t.id);
    expect(ids).toEqual(["billing", "webhooks", "data-retention", "data-sharing"]);
    expect(getSettingsTabsForUser(analyst).map((t) => t.id)).toEqual(["billing", "webhooks"]);
  });

  it("lets only PSP admins propose", () => {
    expect(canProposeDataSettings(pspAdmin)).toBe(true);
    expect(canProposeDataSettings(mlro)).toBe(false);
    expect(canProposeDataSettings(platform)).toBe(false);
  });

  it("never lets the maker review their own change", () => {
    const change = { status: "PENDING_APPROVAL" as const, requestedBy: "alice" };
    expect(canReviewChange(pspAdmin, change)).toBe(false);
    expect(canReviewChange({ ...pspAdmin, username: "ALICE" }, change)).toBe(false);
    expect(canReviewChange(mlro, change)).toBe(true);
    expect(canReviewChange(analyst, change)).toBe(false);
    expect(canReviewChange(mlro, { ...change, status: "APPROVED" })).toBe(false);
  });
});

describe("retention proposal rules", () => {
  it("enforces the 5..25 year range", () => {
    expect(retentionError(4, 5)).toMatch(/shorter than 5/);
    expect(retentionError(26, 5)).toMatch(/longer than 25/);
    expect(retentionError(7.5, 5)).toMatch(/whole number/);
    expect(retentionError(5, 5)).toMatch(/already 5/);
    expect(retentionError(10, 5)).toBeNull();
  });

  it("allows re-proposing the current value only to cancel a waiting shortening", () => {
    expect(retentionError(10, 10, 5, 25, true)).toBeNull();
    expect(retentionChangeNotice(10, 10)).toMatch(/cancels/);
  });

  it("explains the 30-day delay for a shortening", () => {
    expect(retentionChangeNotice(5, 10, 30)).toMatch(/30 days after approval/);
    expect(retentionChangeNotice(12, 10, 30)).toMatch(/as soon as a second reviewer approves/);
  });

  it("describes requested values", () => {
    expect(describeValue("RETENTION_YEARS", "7")).toBe("7 years");
    expect(describeValue("SUPPORT_ACCESS", "true")).toBe("Allowed");
    expect(describeValue("CROSS_PSP_OPT_IN", "false")).toBe("Opted out");
  });
});
