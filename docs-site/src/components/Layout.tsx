import { Link, NavLink, Outlet } from "react-router-dom";
import { SearchBar } from "./SearchBar";
import { INSTALL_DOCS } from "@/lib/docs";

const navDocs = INSTALL_DOCS.filter((doc) => doc.slug !== "index");

export function Layout() {
  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden="true" />
            <span>
              <strong>Hokeka AML</strong>
              <small>Install Suite</small>
            </span>
          </Link>
          <nav className="header-nav" aria-label="Primary">
            <NavLink to="/" end>
              Overview
            </NavLink>
            <NavLink to="/catalog">Catalog</NavLink>
          </nav>
          <SearchBar />
        </div>
      </header>

      <div className="app-body">
        <aside className="sidebar" aria-label="Install processes">
          <p className="sidebar-label">Process docs</p>
          <ol className="sidebar-list">
            {navDocs.map((doc) => (
              <li key={doc.slug}>
                <NavLink
                  to={doc.slug === "index" ? "/" : `/docs/${doc.slug}`}
                  className={({ isActive }) =>
                    isActive ? "sidebar-link active" : "sidebar-link"
                  }
                >
                  <span className="sidebar-order">
                    {doc.installOrder === 99 ? "Dev" : doc.installOrder}
                  </span>
                  <span>{doc.shortTitle ?? doc.title}</span>
                </NavLink>
              </li>
            ))}
          </ol>
        </aside>

        <main className="main-content">
          <Outlet />
        </main>
      </div>

      <footer className="site-footer">
        <p>
          Source markdown lives in{" "}
          <code>docs/install/</code> — this site renders it at build time.
        </p>
      </footer>
    </div>
  );
}
