import { describe, expect, it } from "vitest";
import {
  CONSOLE_TEST_ROLES,
  buildConsoleNavGroups,
  canAccessRoute,
  canPerformAction,
  effectivePermissions,
  mockUserContextForRole,
  routesForRoleMatrix,
} from "./consoleRbac";
import { Permission } from "../types/userManagement";

describe("consoleRbac role matrix", () => {
  it("platform super admin can reach operator routes", () => {
    const user = mockUserContextForRole("SUPER_ADMIN");
    expect(canAccessRoute(user, "/psps")).toBe(true);
    expect(canAccessRoute(user, "/runtime-errors")).toBe(true);
    expect(canAccessRoute(user, "/billing")).toBe(true);
    expect(canPerformAction(user, "ai.settings")).toBe(true);
  });

  it("platform admin can operate the fleet but not cross-tenant user admin", () => {
    const user = mockUserContextForRole("PLATFORM_ADMIN");
    expect(canAccessRoute(user, "/dashboard")).toBe(true);
    expect(canAccessRoute(user, "/runtime-errors")).toBe(true);
    expect(canAccessRoute(user, "/users")).toBe(false);
  });

  it("PSP admin sees tenant organization nav but not platform PSP list", () => {
    const user = mockUserContextForRole("PSP_ADMIN");
    const labels = buildConsoleNavGroups(user).flatMap((g) => g.items.map((i) => i.label));
    expect(labels).toContain("My Organization");
    expect(labels).not.toContain("PSPs");
    expect(canAccessRoute(user, "/organization")).toBe(true);
    expect(canAccessRoute(user, "/psps")).toBe(false);
  });

  it("auditor is read-only for alert disposition", () => {
    const user = mockUserContextForRole("AUDITOR");
    expect(canAccessRoute(user, "/audit")).toBe(true);
    expect(canAccessRoute(user, "/alerts")).toBe(true);
    expect(canPerformAction(user, "alert.resolveDisposition")).toBe(false);
    expect(canPerformAction(user, "rules.manage")).toBe(false);
  });

  it("compliance officer can resolve alerts but not manage platform billing", () => {
    const user = mockUserContextForRole("COMPLIANCE_OFFICER");
    expect(canPerformAction(user, "alert.resolveDisposition")).toBe(true);
    expect(canPerformAction(user, "sar.approve")).toBe(true);
    expect(canPerformAction(user, "billing.platform")).toBe(false);
  });

  it("PSP user lacks rules management route", () => {
    const user = mockUserContextForRole("PSP_USER");
    expect(canAccessRoute(user, "/rules-generation")).toBe(false);
    expect(canAccessRoute(user, "/screening")).toBe(true);
  });

  it("documents a non-empty route list for every test role", () => {
    for (const role of CONSOLE_TEST_ROLES) {
      const routes = routesForRoleMatrix(role);
      expect(routes.length, role).toBeGreaterThan(0);
      expect(routes, role).toContain("/dashboard");
    }
  });

  it("screening analyst can access screening but not audit logs", () => {
    const user = mockUserContextForRole("SCREENING_ANALYST");
    expect(canAccessRoute(user, "/screening")).toBe(true);
    expect(canAccessRoute(user, "/audit")).toBe(false);
    const set = effectivePermissions(user);
    expect(set.has(Permission.OVERRIDE_SCREENING_MATCH)).toBe(true);
  });
});
