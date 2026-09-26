import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const FORBIDDEN = /openrouter|\bjev\b|anthropic|openai|\bprompt\b|\btoken\b|\bcost\b|gpt-/i;

describe("PSP-facing AI disclosure guards", () => {
  it("AiVerdictPanel uses generic Hokeka branding and hides vendor fields from tenants", () => {
    const source = readFileSync(
      resolve(__dirname, "../components/Jev/AiVerdictPanel.tsx"),
      "utf8"
    );
    expect(source).toContain('const PRODUCT_TITLE = "Hokeka AI recommendation"');
    expect(source).toContain("Hokeka AI recommendation");
    expect(source).not.toMatch(/Hokeka Intelligence/i);
    expect(source).not.toContain("OpenRouter");
    expect(source).not.toMatch(/\bJEV\b/);
    expect(source).toContain("function TenantAuditEntryBlock");
    const tenantBlock = source.split("function TenantAuditEntryBlock")[1]?.split("export default")[0] ?? "";
    expect(tenantBlock).not.toContain("modelId");
    expect(tenantBlock).not.toMatch(/Audit\s*#/);
    expect(FORBIDDEN.test(tenantBlock)).toBe(false);
  });

  it("Screening page uses standard Hokeka AI recommendation panel title", () => {
    const source = readFileSync(
      resolve(__dirname, "../pages/Screening/ScreeningPage.tsx"),
      "utf8"
    );
    expect(source).toContain("AiVerdictPanel");
    expect(source).not.toContain('title="Hokeka Intelligence"');
  });

  it("settings tabs hide JEV operator tab from PSP roles", () => {
    const source = readFileSync(resolve(__dirname, "settingsTabs.ts"), "utf8");
    expect(source).toContain('"JEV AI"');
    expect(source).toContain("isPlatformAdmin");
  });
});
