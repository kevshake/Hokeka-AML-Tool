import { Link } from "react-router-dom";
import { useOutletContext } from "react-router-dom";
import type { ManagementOperation, OpenApiSpec } from "@/lib/managementApi/types";

interface MgmtContext {
  spec: OpenApiSpec | null;
  operations: ManagementOperation[];
}

/** Overview landing for the Management API product (separate from PSP integration API). */
export function ManagementHomePage() {
  const { spec, operations } = useOutletContext<MgmtContext>();

  return (
    <article className="mgmt-doc">
      <p className="mgmt-eyebrow">Control Plane · Console operators</p>
      <h1>Management API</h1>
      <p className="mgmt-lead">
        Programmatic administration for users, roles, PSP tenants, API keys, webhooks,
        rules, edge nodes, export jobs, audit logs, and platform toggles. This surface
        is session-authenticated (Console login) and tenant-scoped — not the PSP
        transaction / screening integration API.
      </p>

      <div className="mgmt-callout">
        <h2>Authentication</h2>
        <ol>
          <li>
            <code>POST /auth/login</code> with Console credentials.
          </li>
          <li>
            Send the <code>JSESSIONID</code> cookie on every Management request (see each
            endpoint page for samples).
          </li>
        </ol>
        <p>
          Role requirements follow{" "}
          <code>docs/CONSOLE_ROLE_MATRIX.md</code> (e.g. <code>MANAGE_USERS</code>,{" "}
          <code>MANAGE_PSP</code>, <code>VIEW_AUDIT_LOGS</code>).
        </p>
      </div>

      <h2>Environments</h2>
      <ul>
        {(spec?.servers ?? []).map((server) => (
          <li key={server.url}>
            <strong>{server.description ?? "Server"}</strong> — <code>{server.url}</code>
          </li>
        ))}
      </ul>

      <h2>Quick links</h2>
      <ul className="mgmt-quick-links">
        <li>
          <Link to="/management-api/reference">Browse all {operations.length} endpoints</Link>
        </li>
        <li>
          <a href="/management-api/openapi.json">OpenAPI JSON</a>
        </li>
        <li>
          <a href="/management-api/llms.txt">llms.txt index</a>
        </li>
      </ul>

      <h2>Not in this API</h2>
      <p>
        Transaction evaluation, screening hits, cases, and alerts are part of the PSP
        integration / monitoring API. See{" "}
        <code>docs/PSP_API_GUIDE.md</code>{" "}
        and the data residency contract in{" "}
        <code>docs/EDGE_DATA_RESIDENCY_CONTRACT.md</code>.
      </p>
    </article>
  );
}
