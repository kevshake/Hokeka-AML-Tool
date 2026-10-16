#!/usr/bin/env node
/**
 * Writes management-api/llms.txt index for AI clients (Flagright-style).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicRoot = path.resolve(__dirname, "../public/management-api");
const spec = JSON.parse(
  fs.readFileSync(path.join(publicRoot, "openapi.json"), "utf8"),
);

/** Builds a stable slug for an operation detail page. */
function operationSlug(method, pathKey, operation) {
  if (operation?.operationId) {
    return operation.operationId;
  }
  return `${method.toLowerCase()}-${pathKey.replace(/\//g, "-").replace(/[{}]/g, "")}`;
}

/** Builds a Flagright-style title from summary or method + path. */
function pageTitle(method, pathKey, operation) {
  if (operation?.summary) {
    return operation.summary;
  }
  return `${method} ${pathKey}`;
}

const lines = [
  "> For clean Markdown of any endpoint page, open the docs site Management API reference.",
  "> PSP transaction / screening APIs are documented separately (integration / FRAML-style guide).",
  "",
  "# Management API",
  "",
  "## API Reference",
  "",
];

for (const pathKey of Object.keys(spec.paths ?? {}).sort()) {
  const item = spec.paths[pathKey];
  for (const method of ["get", "post", "put", "patch", "delete"]) {
    const op = item[method];
    if (!op) continue;
    const slug = operationSlug(method, pathKey, op);
    const title = pageTitle(method, pathKey, op);
    lines.push(
      `- API Reference [${title}](/management-api/reference/${slug})`,
    );
  }
}

lines.push(
  "",
  "## OpenAPI Specification",
  "",
  "The raw OpenAPI 3.0 specification for this API is available at:",
  "- [OpenAPI JSON](/management-api/openapi.json)",
  "",
);

fs.writeFileSync(path.join(publicRoot, "llms.txt"), lines.join("\n") + "\n");
console.log("Wrote management-api/llms.txt");
