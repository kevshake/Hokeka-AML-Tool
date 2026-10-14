#!/usr/bin/env node
/**
 * Ensures the committed Management API OpenAPI spec exists, has a minimum path count, and contains
 * no operator-only Hokeka AI admin surfaces (PSP-visible doc.hokeka.com rule).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicRoot = path.resolve(__dirname, "../public/management-api");
const specPath = path.join(publicRoot, "openapi.json");
const llmsPath = path.join(publicRoot, "llms.txt");

/** Fails the process with a GitHub Actions–style error line. */
function fail(message) {
  console.error(`::error::${message}`);
  process.exit(1);
}

/** Returns true when text contains a case-insensitive whole-word match. */
function hasWholeWord(text, word) {
  const re = new RegExp(`\\b${word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\b`, "i");
  return re.test(text);
}

/** Scans OpenAPI paths and llms.txt for forbidden operator-only AI admin documentation. */
function assertNoOperatorOnlyAiSurfaces(spec, llmsText) {
  const paths = Object.keys(spec.paths ?? {});
  const forbiddenPathFragments = ["/ai/engines", "/ai/psps", "/ai/status"];
  for (const fragment of forbiddenPathFragments) {
    if (paths.some((p) => p.includes(fragment))) {
      fail(`Management OpenAPI documents forbidden path fragment: ${fragment}`);
    }
  }

  const serializedSpec = JSON.stringify(spec);
  if (serializedSpec.includes("promptVersion")) {
    fail("Management OpenAPI must not contain promptVersion (AI operator field)");
  }
  if (serializedSpec.includes("AiEngineSetting")) {
    fail("Management OpenAPI must not contain AiEngineSetting schema");
  }

  const forbiddenTerms = ["JEV", "OpenRouter", "Laya"];
  for (const term of forbiddenTerms) {
    if (hasWholeWord(serializedSpec, term) || hasWholeWord(llmsText, term)) {
      fail(`Management API docs must not mention ${term}`);
    }
  }

  if (/\bmodelId\b/.test(serializedSpec) || /\bmodelId\b/.test(llmsText)) {
    fail("Management API docs must not expose modelId");
  }
  if (/\baiProvider\b/i.test(serializedSpec) || /\baiProvider\b/i.test(llmsText)) {
    fail("Management API docs must not expose aiProvider");
  }
  if (/\btokenCount\b/i.test(serializedSpec) || /\btokenCount\b/i.test(llmsText)) {
    fail("Management API docs must not expose token counts");
  }
  if (/\b(inputTokens|outputTokens)\b/i.test(serializedSpec)) {
    fail("Management API docs must not expose LLM token fields");
  }
  if (/\baiCostUsd\b/i.test(serializedSpec)) {
    fail("Management API docs must not expose AI cost fields");
  }

  // Allowed: screening data-source `provider` on wallet/VASP response schemas only.
  const schemas = spec.components?.schemas ?? {};
  for (const [name, schema] of Object.entries(schemas)) {
    const body = JSON.stringify(schema);
    if (body.includes('"provider"')
        && name !== "WalletScreeningResponse"
        && name !== "VaspScreeningResponse") {
      fail(`Unexpected provider field on schema ${name} (screening provider is allowed only on wallet/VASP responses)`);
    }
  }

  for (const line of llmsText.split("\n")) {
    if (/\[Ai - /i.test(line) || line.includes("/ai/engines") || line.includes("/ai/psps")) {
      fail(`llms.txt must not index operator-only AI admin endpoints: ${line.trim()}`);
    }
  }
}

if (!fs.existsSync(specPath)) {
  fail(
    "Missing docs-site/public/management-api/openapi.json — run ManagementOpenApiExportIT with MANAGEMENT_OPENAPI_EXPORT=true",
  );
}

if (!fs.existsSync(llmsPath)) {
  fail("Missing docs-site/public/management-api/llms.txt — run npm run prebuild in docs-site");
}

const spec = JSON.parse(fs.readFileSync(specPath, "utf8"));
const llmsText = fs.readFileSync(llmsPath, "utf8");
const paths = Object.keys(spec.paths ?? {});
const minPaths = 40;

if (paths.length < minPaths) {
  fail(
    `Management OpenAPI has ${paths.length} paths (expected at least ${minPaths}). Regenerate from springdoc.`,
  );
}

if (spec.info?.title !== "Hokeka Management API") {
  fail("Wrong OpenAPI info.title for management group");
}

assertNoOperatorOnlyAiSurfaces(spec, llmsText);

console.log(`Management OpenAPI OK (${paths.length} paths, PSP-safe AI guard passed)`);
