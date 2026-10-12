import { describe, expect, it } from "vitest";
import { canAccessAiSettingsTab, getSettingsTabsForUser } from "./settingsTabs";

describe("settingsTabs", () => {
  it("hides Hokeka AI tab for PSP admin and PSP user roles", () => {
    const pspAdmin = { pspId: 42, role: { name: "PSP_ADMIN" } };
    const pspUser = { pspId: 42, role: { name: "PSP_USER" } };

    expect(canAccessAiSettingsTab(pspAdmin)).toBe(false);
    expect(canAccessAiSettingsTab(pspUser)).toBe(false);
    expect(getSettingsTabsForUser(pspAdmin).some((t) => t.id === "ai")).toBe(false);
    expect(getSettingsTabsForUser(pspUser).some((t) => t.id === "ai")).toBe(false);
  });

  it("shows Hokeka AI tab only for platform operators", () => {
    const platformAdmin = { pspId: 0, role: { name: "PLATFORM_ADMIN" } };
    const superAdmin = { pspId: 0, role: { name: "SUPER_ADMIN" } };

    expect(canAccessAiSettingsTab(platformAdmin)).toBe(true);
    expect(canAccessAiSettingsTab(superAdmin)).toBe(true);
    expect(getSettingsTabsForUser(platformAdmin).some((t) => t.id === "ai")).toBe(true);
  });
});
