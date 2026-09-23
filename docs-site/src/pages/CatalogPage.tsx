import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { INSTALL_DOCS } from "@/lib/docs";
import { ALL_AUDIENCES } from "@/lib/docMeta";
import type { Audience, SortKey } from "@/lib/types";

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: "installOrder", label: "Install order" },
  { value: "title", label: "Title (A–Z)" },
  { value: "audience", label: "Audience" },
];

export function CatalogPage() {
  const [sortKey, setSortKey] = useState<SortKey>("installOrder");
  const [audienceFilter, setAudienceFilter] = useState<Audience | "all">("all");
  const [tagFilter, setTagFilter] = useState<string>("all");

  const allTags = useMemo(() => {
    const tags = new Set<string>();
    INSTALL_DOCS.forEach((doc) => doc.tags.forEach((tag) => tags.add(tag)));
    return [...tags].sort();
  }, []);

  const filtered = useMemo(() => {
    let docs = INSTALL_DOCS.filter((doc) => doc.slug !== "index");

    if (audienceFilter !== "all") {
      docs = docs.filter((doc) => doc.audiences.includes(audienceFilter));
    }

    if (tagFilter !== "all") {
      docs = docs.filter((doc) => doc.tags.includes(tagFilter));
    }

    docs = [...docs].sort((a, b) => {
      switch (sortKey) {
        case "title":
          return a.title.localeCompare(b.title);
        case "audience":
          return (
            a.audiences[0]?.localeCompare(b.audiences[0] ?? "") ??
            a.title.localeCompare(b.title)
          );
        case "installOrder":
        default:
          return a.installOrder - b.installOrder;
      }
    });

    return docs;
  }, [sortKey, audienceFilter, tagFilter]);

  return (
    <div className="page">
      <header className="page-header compact">
        <p className="eyebrow">Browse</p>
        <h1>Install catalog</h1>
        <p className="lede">
          Sort and filter the supported install runbooks. Each page renders live
          markdown from <code>docs/install/</code>.
        </p>
      </header>

      <div className="catalog-controls">
        <label>
          Sort by
          <select
            value={sortKey}
            onChange={(event) => setSortKey(event.target.value as SortKey)}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          Audience
          <select
            value={audienceFilter}
            onChange={(event) =>
              setAudienceFilter(event.target.value as Audience | "all")
            }
          >
            <option value="all">All audiences</option>
            {ALL_AUDIENCES.filter((a) => a !== "Overview").map((audience) => (
              <option key={audience} value={audience}>
                {audience}
              </option>
            ))}
          </select>
        </label>

        <label>
          Tag
          <select
            value={tagFilter}
            onChange={(event) => setTagFilter(event.target.value)}
          >
            <option value="all">All tags</option>
            {allTags.map((tag) => (
              <option key={tag} value={tag}>
                {tag}
              </option>
            ))}
          </select>
        </label>
      </div>

      <p className="catalog-count">
        Showing {filtered.length} of{" "}
        {INSTALL_DOCS.filter((d) => d.slug !== "index").length} process docs
      </p>

      <div className="catalog-grid">
        {filtered.map((doc) => (
          <article key={doc.slug} className="catalog-card">
            {doc.illustration && (
              <Link to={`/docs/${doc.slug}`} className="catalog-thumb">
                <img
                  src={`/illustrations/${doc.illustration}`}
                  alt=""
                  loading="lazy"
                />
              </Link>
            )}
            <div className="catalog-card-body">
              <p className="catalog-meta">
                {doc.installOrder === 99
                  ? "Developer"
                  : `Install step ${doc.installOrder}`}{" "}
                · {doc.audiences.join(", ")}
              </p>
              <h2>
                <Link to={`/docs/${doc.slug}`}>{doc.title}</Link>
              </h2>
              <p>{doc.excerpt}</p>
              <div className="tag-row">
                {doc.tags.map((tag) => (
                  <span key={tag} className="tag">
                    {tag}
                  </span>
                ))}
              </div>
            </div>
          </article>
        ))}
      </div>

      {filtered.length === 0 && (
        <p className="catalog-empty">No documents match the current filters.</p>
      )}
    </div>
  );
}
