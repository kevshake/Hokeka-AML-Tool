import { Link, NavLink, Outlet, useParams } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import {
  flattenOperations,
  loadManagementOpenApi,
} from "@/lib/managementApi/loadSpec";
import type { ManagementOperation, OpenApiSpec } from "@/lib/managementApi/types";
import {
  buildManagementSearchIndex,
  searchManagementOperations,
} from "@/lib/managementApi/searchIndex";

/** Layout for Management API docs: grouped left nav + search + outlet. */
export function ManagementShell() {
  const [spec, setSpec] = useState<OpenApiSpec | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const { slug } = useParams();

  useEffect(() => {
    loadManagementOpenApi()
      .then(setSpec)
      .catch((e: Error) => setError(e.message));
  }, []);

  const operations = useMemo(
    () => (spec ? flattenOperations(spec) : []),
    [spec],
  );

  const searchIndex = useMemo(
    () => (operations.length ? buildManagementSearchIndex(operations) : null),
    [operations],
  );

  const searchHits = useMemo(() => {
    if (!searchIndex || !query.trim()) return [];
    const slugs = searchManagementOperations(searchIndex, query, 12);
    return slugs
      .map((s) => operations.find((o) => o.slug === s))
      .filter((o): o is ManagementOperation => Boolean(o));
  }, [searchIndex, query, operations]);

  const grouped = useMemo(() => {
    const map = new Map<string, ManagementOperation[]>();
    for (const op of operations) {
      const list = map.get(op.group) ?? [];
      list.push(op);
      map.set(op.group, list);
    }
    return [...map.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [operations]);

  return (
    <div className="mgmt-shell">
      <header className="mgmt-header">
        <div className="mgmt-header-inner">
          <Link to="/" className="brand mgmt-brand-link">
            <span className="brand-mark" aria-hidden="true" />
            <span>
              <strong>Hokeka Docs</strong>
              <small>Management API</small>
            </span>
          </Link>
          <nav className="header-nav" aria-label="Doc products">
            <NavLink to="/">Install</NavLink>
            <NavLink to="/management-api">Management API</NavLink>
          </nav>
          <div className="mgmt-search-wrap">
            <input
              type="search"
              className="mgmt-search"
              placeholder="Search management endpoints…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Search management API"
            />
            {query && searchHits.length > 0 && (
              <ul className="mgmt-search-results">
                {searchHits.map((hit) => (
                  <li key={hit.slug}>
                    <Link
                      to={`/management-api/reference/${hit.slug}`}
                      onClick={() => setQuery("")}
                    >
                      <span className={`http-badge http-${hit.method}`}>
                        {hit.method.toUpperCase()}
                      </span>
                      {hit.summary}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </header>

      <div className="mgmt-body">
        <aside className="mgmt-sidebar" aria-label="Management API resources">
          {error && <p className="mgmt-error">{error}</p>}
          {!spec && !error && <p className="mgmt-muted">Loading OpenAPI…</p>}
          {grouped.map(([group, ops]) => (
            <section key={group} className="mgmt-nav-group">
              <h2>{group}</h2>
              <ul>
                {ops.map((op) => (
                  <li key={op.slug}>
                    <NavLink
                      to={`/management-api/reference/${op.slug}`}
                      className={({ isActive }) =>
                        isActive || slug === op.slug
                          ? "mgmt-nav-link active"
                          : "mgmt-nav-link"
                      }
                    >
                      <span className={`http-badge http-${op.method}`}>
                        {op.method.toUpperCase()}
                      </span>
                      <span>{op.summary}</span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </aside>

        <main className="mgmt-main">
          <Outlet context={{ spec, operations }} />
        </main>
      </div>
    </div>
  );
}
