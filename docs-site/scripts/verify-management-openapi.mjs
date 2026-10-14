#!/usr/bin/env node
/**
 * Ensures the committed Management API OpenAPI spec exists and has a minimum path count.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const specPath = path.resolve(
  __dirname,
  "../public/management-api/openapi.json",
);

if (!fs.existsSync(specPath)) {
  console.error(
    "::error::Missing docs-site/public/management-api/openapi.json — run ManagementOpenApiExportIT with MANAGEMENT_OPENAPI_EXPORT=true",
  );
  process.exit(1);
}

const spec = JSON.parse(fs.readFileSync(specPath, "utf8"));
const paths = Object.keys(spec.paths ?? {});
const minPaths = 40;

if (paths.length < minPaths) {
  console.error(
    `::error::Management OpenAPI has ${paths.length} paths (expected at least ${minPaths}). Regenerate from springdoc.`,
  );
  process.exit(1);
}

if (spec.info?.title !== "Hokeka Management API") {
  console.error("::error::Wrong OpenAPI info.title for management group");
  process.exit(1);
}

console.log(`Management OpenAPI OK (${paths.length} paths)`);
