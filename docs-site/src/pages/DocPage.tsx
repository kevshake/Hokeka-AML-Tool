import { Link, useParams } from "react-router-dom";
import { DocHero } from "@/components/DocHero";
import { MarkdownRenderer } from "@/components/MarkdownRenderer";
import { getDocBySlug, INSTALL_DOCS } from "@/lib/docs";

export function DocPage() {
  const { slug = "" } = useParams();
  const doc = getDocBySlug(slug);

  if (!doc) {
    return (
      <div className="page">
        <h1>Document not found</h1>
        <p>
          <Link to="/catalog">Return to catalog</Link>
        </p>
      </div>
    );
  }

  const ordered = INSTALL_DOCS.filter((d) => d.slug !== "index").sort(
    (a, b) => a.installOrder - b.installOrder,
  );
  const idx = ordered.findIndex((d) => d.slug === doc.slug);
  const prev = idx > 0 ? ordered[idx - 1] : null;
  const next = idx >= 0 && idx < ordered.length - 1 ? ordered[idx + 1] : null;

  return (
    <div className="page">
      <header className="page-header compact">
        <p className="eyebrow">{doc.audiences.join(" · ")}</p>
        <h1>{doc.title}</h1>
        {doc.subtitle && <p className="lede">{doc.subtitle}</p>}
        <div className="tag-row">
          {doc.tags.map((tag) => (
            <span key={tag} className="tag">
              {tag}
            </span>
          ))}
        </div>
      </header>

      <DocHero doc={doc} />
      <MarkdownRenderer content={doc.content} />

      <nav className="doc-pager" aria-label="Document pagination">
        {prev ? (
          <Link to={`/docs/${prev.slug}`} className="pager-link prev">
            ← {prev.shortTitle ?? prev.title}
          </Link>
        ) : (
          <span />
        )}
        {next ? (
          <Link to={`/docs/${next.slug}`} className="pager-link next">
            {next.shortTitle ?? next.title} →
          </Link>
        ) : (
          <span />
        )}
      </nav>
    </div>
  );
}
