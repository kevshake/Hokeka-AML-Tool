import { Link } from "react-router-dom";
import { DocHero } from "@/components/DocHero";
import { MarkdownRenderer } from "@/components/MarkdownRenderer";
import { getDocBySlug, INSTALL_DOCS } from "@/lib/docs";

export function HomePage() {
  const indexDoc = getDocBySlug("index");
  const processes = INSTALL_DOCS.filter((doc) => doc.slug !== "index").sort(
    (a, b) => a.installOrder - b.installOrder,
  );

  if (!indexDoc) {
    return <p>Install index not found.</p>;
  }

  return (
    <div className="page">
      <header className="page-header">
        <p className="eyebrow">Hokeka AML Tool</p>
        <h1>Installation Process Map</h1>
        <p className="lede">
          Canonical install paths for <strong>Edge Node</strong>,{" "}
          <strong>Control Plane</strong>, and <strong>Console</strong>. Browse
          the catalog, search across all runbooks, or follow the ordered go-live
          sequence.
        </p>
        <div className="page-actions">
          <Link to="/catalog" className="btn btn-primary">
            Browse catalog
          </Link>
        </div>
      </header>

      <DocHero doc={indexDoc} />

      <section className="quick-cards" aria-label="Install processes">
        {processes.map((doc) => (
          <Link key={doc.slug} to={`/docs/${doc.slug}`} className="quick-card">
            <span className="quick-card-order">
              {doc.installOrder === 99 ? "Dev" : `Step ${doc.installOrder}`}
            </span>
            <strong>{doc.shortTitle ?? doc.title}</strong>
            <span className="quick-card-audience">{doc.audiences.join(" · ")}</span>
            <p>{doc.excerpt}</p>
          </Link>
        ))}
      </section>

      <section className="index-markdown">
        <MarkdownRenderer content={indexDoc.content} />
      </section>
    </div>
  );
}
