import FlexSearch from "flexsearch";
import { INSTALL_DOCS } from "./docs";
import type { SearchResult } from "./types";

interface SearchDocument {
  slug: string;
  title: string;
  subtitle: string;
  content: string;
  tags: string;
  audiences: string;
}

const index = new FlexSearch.Document<SearchDocument, false>({
  document: {
    id: "slug",
    index: ["title", "subtitle", "content", "tags", "audiences"],
  },
  tokenize: "forward",
  cache: true,
  context: {
    resolution: 9,
    depth: 2,
    bidirectional: true,
  },
});

for (const doc of INSTALL_DOCS) {
  index.add({
    slug: doc.slug,
    title: doc.title,
    subtitle: doc.subtitle,
    content: doc.content.replace(/[#*`>|]/g, " "),
    tags: doc.tags.join(" "),
    audiences: doc.audiences.join(" "),
  });
}

export function searchDocs(query: string, limit = 12): SearchResult[] {
  const trimmed = query.trim();
  if (!trimmed) return [];

  const raw = index.search(trimmed, { limit, enrich: true });
  const seen = new Map<string, number>();

  for (const group of raw) {
    if (!group || typeof group !== "object" || !("result" in group)) continue;
    const hits = group.result as Array<{ id?: string; score?: number } | string>;
    for (const hit of hits) {
      const slug = typeof hit === "string" ? hit : String(hit.id ?? "");
      if (!slug) continue;
      const score = typeof hit === "object" ? (hit.score ?? 1) : 1;
      seen.set(slug, Math.max(seen.get(slug) ?? 0, score));
    }
  }

  return [...seen.entries()]
    .map(([slug, score]) => {
      const doc = INSTALL_DOCS.find((d) => d.slug === slug);
      if (!doc) return null;
      return {
        slug: doc.slug,
        title: doc.title,
        excerpt: doc.excerpt,
        score,
      } satisfies SearchResult;
    })
    .filter((item): item is SearchResult => item !== null)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit);
}
