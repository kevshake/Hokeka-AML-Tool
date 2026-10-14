import type { ManagementOperation, OpenApiSpec, HttpMethod } from "./types";

let cachedSpec: OpenApiSpec | null = null;

/** Loads the committed springdoc Management OpenAPI JSON bundled at build time. */
export async function loadManagementOpenApi(): Promise<OpenApiSpec> {
  if (cachedSpec) {
    return cachedSpec;
  }
  const response = await fetch("/management-api/openapi.json");
  if (!response.ok) {
    throw new Error(`Failed to load management OpenAPI: ${response.status}`);
  }
  cachedSpec = (await response.json()) as OpenApiSpec;
  return cachedSpec;
}

/** Derives a URL slug for an operation detail route. */
export function operationSlug(
  method: HttpMethod,
  path: string,
  operationId?: string,
): string {
  if (operationId && operationId.trim()) {
    return operationId.trim();
  }
  return `${method}-${path.replace(/\//g, "-").replace(/[{}]/g, "")}`.replace(
    /-+/g,
    "-",
  );
}

/** Maps a servlet path to a left-nav resource group. */
export function resourceGroupForPath(path: string): string {
  const segment = path.split("/").filter(Boolean)[0] ?? "other";
  const labels: Record<string, string> = {
    users: "Users & accounts",
    roles: "Roles & permissions",
    auth: "Roles & permissions",
    psps: "PSPs & tenants",
    admin: "Platform admin",
    edge: "Edge nodes",
    webhooks: "Webhooks",
    rules: "Rules",
    audit: "Audit logs",
    console: "Export jobs",
    settings: "Settings",
    ai: "Hokeka AI settings",
  };
  return labels[segment] ?? segment;
}

/** Flattens OpenAPI paths into sorted ManagementOperation records for nav and search. */
export function flattenOperations(spec: OpenApiSpec): ManagementOperation[] {
  const methods: HttpMethod[] = ["get", "post", "put", "patch", "delete"];
  const ops: ManagementOperation[] = [];

  for (const path of Object.keys(spec.paths).sort()) {
    const item = spec.paths[path];
    for (const method of methods) {
      const operation = item[method];
      if (!operation) continue;
      const summary =
        operation.summary ?? `${method.toUpperCase()} ${path}`;
      ops.push({
        slug: operationSlug(method, path, operation.operationId),
        method,
        path,
        summary,
        description: operation.description ?? "",
        group: resourceGroupForPath(path),
        operation,
      });
    }
  }

  return ops.sort((a, b) =>
    a.group.localeCompare(b.group) ||
    a.path.localeCompare(b.path) ||
    a.method.localeCompare(b.method),
  );
}

/** Finds one operation by slug from a pre-flattened list. */
export function findOperation(
  operations: ManagementOperation[],
  slug: string,
): ManagementOperation | undefined {
  return operations.find((op) => op.slug === slug);
}
