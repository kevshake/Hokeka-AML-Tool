import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  DOCUMENTED_PRIMARY_CONSOLE_ROUTES,
  LEGACY_CONSOLE_ROUTE_ALIASES,
} from "./documentedConsoleRoutes";

describe("documented Console routes", () => {
  it("registers legacy doc/bookmark aliases in App.tsx", () => {
    const appSource = readFileSync(resolve(__dirname, "../App.tsx"), "utf8");
    expect(appSource).toContain("LEGACY_CONSOLE_ROUTE_ALIASES.map");
    expect(appSource).toContain('path={legacySegment}');
    expect(appSource).toContain('<Navigate to={to} replace />');
    expect(LEGACY_CONSOLE_ROUTE_ALIASES.length).toBeGreaterThan(0);
  });

  it("maps legacy aliases to current primary routes", () => {
    for (const { to } of LEGACY_CONSOLE_ROUTE_ALIASES) {
      expect(DOCUMENTED_PRIMARY_CONSOLE_ROUTES).toContain(to);
    }
  });

  it("includes the in-app messages inbox route documented in NOTIFICATIONS.md", () => {
    expect(DOCUMENTED_PRIMARY_CONSOLE_ROUTES).toContain("/messages");
  });
});
