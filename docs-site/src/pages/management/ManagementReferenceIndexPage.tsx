import { Link, useOutletContext } from "react-router-dom";
import type { ManagementOperation, OpenApiSpec } from "@/lib/managementApi/types";

interface MgmtContext {
  spec: OpenApiSpec | null;
  operations: ManagementOperation[];
}

/** Flat index of all Management API endpoints grouped by resource. */
export function ManagementReferenceIndexPage() {
  const { operations } = useOutletContext<MgmtContext>();
  const groups = new Map<string, ManagementOperation[]>();
  for (const op of operations) {
    const list = groups.get(op.group) ?? [];
    list.push(op);
    groups.set(op.group, list);
  }

  return (
    <article className="mgmt-doc">
      <h1>API reference</h1>
      <p>
        {operations.length} operator endpoints generated from springdoc OpenAPI (
        <a href="/management-api/openapi.json">download JSON</a>).
      </p>
      {[...groups.entries()].map(([group, ops]) => (
        <section key={group}>
          <h2>{group}</h2>
          <ul className="mgmt-ref-index">
            {ops.map((op) => (
              <li key={op.slug}>
                <Link to={`/management-api/reference/${op.slug}`}>
                  <span className={`http-badge http-${op.method}`}>
                    {op.method.toUpperCase()}
                  </span>
                  {op.summary}
                  <code className="mgmt-path-inline">{op.path}</code>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </article>
  );
}
