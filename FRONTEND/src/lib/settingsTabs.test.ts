import { describe, expect, it } from "vitest";
import { canAccessJevSettingsTab, getSettingsTabsForUser } from "./settingsTabs";

describe("settingsTabs", () => {
  it("hides JEV AI tab for PSP admin and PSP user roles", () => {
    const pspAdmin = { pspId: 42, role: { name: "PSP_ADMIN" } };
    const pspUser = { pspId: 42, role: { name: "PSP_USER" } };

    expect(canAccessJevSettingsTab(pspAdmin)).toBe(false);
    expect(canAccessJevSettingsTab(pspUser)).toBe(false);
    expect(getSettingsTabsForUser(pspAdmin).some((t) => t.id === "jev")).toBe(false);
    expect(getSettingsTabsForUser(pspUser).some((t) => t.id === "jev")).toBe(false);
  });

  it("shows JEV AI tab only for platform operators", () => {
    const platformAdmin = { pspId: 0, role: { name: "PLATFORM_ADMIN" } };
    const superAdmin = { pspId: 0, role: { name: "SUPER_ADMIN" } };

    expect(canAccessJevSettingsTab(platformAdmin)).toBe(true);
    expect(canAccessJevSettingsTab(superAdmin)).toBe(true);
    expect(getSettingsTabsForUser(platformAdmin).some((t) => t.id === "jev")).toBe(true);
  });
});
