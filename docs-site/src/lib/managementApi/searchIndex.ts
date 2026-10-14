import FlexSearch from "flexsearch";
import type { ManagementOperation } from "./types";

/** Builds a client-side FlexSearch index over Management API operations. */
export function buildManagementSearchIndex(
  operations: ManagementOperation[],
): FlexSearch.Document<ManagementOperation, true> {
  const index = new FlexSearch.Document<ManagementOperation, true>({
    document: {
      id: "slug",
      index: ["summary", "path", "group", "description"],
    },
    tokenize: "forward",
  });

  for (const op of operations) {
    index.add(op);
  }
  return index;
}

/** Runs a search query and returns matching operation slugs. */
export function searchManagementOperations(
  index: FlexSearch.Document<ManagementOperation, true>,
  query: string,
  limit = 20,
): string[] {
  if (!query.trim()) {
    return [];
  }
  const result = index.search(query, { limit }) as { field: string; result: string[] }[];
  const slugs = new Set<string>();
  for (const block of result) {
    for (const slug of block.result) {
      slugs.add(slug);
    }
  }
  return [...slugs];
}
