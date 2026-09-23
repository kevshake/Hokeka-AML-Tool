import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { searchDocs } from "@/lib/search";
import type { SearchResult } from "@/lib/types";

export function SearchBar() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    setResults(searchDocs(query));
    setOpen(true);
  }, [query]);

  useEffect(() => {
    function onDocClick(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, []);

  function docPath(slug: string) {
    return slug === "index" ? "/" : `/docs/${slug}`;
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (results[0]) {
      navigate(docPath(results[0].slug));
      setOpen(false);
      setQuery("");
    }
  }

  return (
    <div className="search-wrap" ref={containerRef}>
      <form onSubmit={handleSubmit} role="search">
        <label htmlFor="doc-search" className="sr-only">
          Search install docs
        </label>
        <input
          id="doc-search"
          type="search"
          placeholder="Search install docs…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onFocus={() => query && setOpen(true)}
          autoComplete="off"
        />
      </form>
      {open && query && (
        <ul className="search-results" role="listbox">
          {results.length === 0 ? (
            <li className="search-empty">No matches for “{query}”</li>
          ) : (
            results.map((result) => (
              <li key={result.slug}>
                <Link
                  to={docPath(result.slug)}
                  onClick={() => {
                    setOpen(false);
                    setQuery("");
                  }}
                  role="option"
                >
                  <strong>{result.title}</strong>
                  <span>{result.excerpt}</span>
                </Link>
              </li>
            ))
          )}
        </ul>
      )}
    </div>
  );
}
