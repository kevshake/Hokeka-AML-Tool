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
    expect(source).toContain('title = "Hokeka AI recommendation"');
    expect(source).toContain("Hokeka Intelligence suggestions");
    expect(source).not.toContain("OpenRouter");
    expect(source).not.toMatch(/\bJEV\b/);
    expect(source).toContain("function TenantAuditEntryBlock");
    expect(source).toContain("function OperatorAuditEntryBlock");
    const tenantBlock = source.split("function TenantAuditEntryBlock")[1]?.split("function OperatorAuditEntryBlock")[0] ?? "";
    expect(tenantBlock).not.toContain("modelId");
    expect(FORBIDDEN.test(tenantBlock)).toBe(false);
  });

  it("settings tabs hide JEV operator tab from PSP roles", () => {
    const source = readFileSync(resolve(__dirname, "settingsTabs.ts"), "utf8");
    expect(source).toContain('"JEV AI"');
    expect(source).toContain("isPlatformAdmin");
  });
});
