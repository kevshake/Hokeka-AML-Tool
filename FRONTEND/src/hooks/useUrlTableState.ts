import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";

export interface UrlTableState {
  page: number;
  size: number;
  sort?: string;
  filters: Record<string, string>;
}

/** Parses integer query params safely with a default fallback. */
function readInt(raw: string | null, fallback: number): number {
  if (!raw) return fallback;
  const n = Number.parseInt(raw, 10);
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}

/**
 * Keeps table pagination, sort, and filter state in the URL so list pages are shareable
 * and server-side exports can replay the same filter map.
 */
export function useUrlTableState(defaultSize = 25, reservedKeys: string[] = ["page", "size", "sort"]) {
  const [searchParams, setSearchParams] = useSearchParams();

  const state: UrlTableState = useMemo(() => {
    const filters: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      if (!reservedKeys.includes(key) && value !== "") {
        filters[key] = value;
      }
    });
    return {
      page: readInt(searchParams.get("page"), 0),
      size: readInt(searchParams.get("size"), defaultSize),
      sort: searchParams.get("sort") || undefined,
      filters,
    };
  }, [searchParams, defaultSize, reservedKeys]);

  /** Merges partial updates into the current URL query string without dropping unrelated params. */
  const patchParams = useCallback(
    (patch: Record<string, string | number | undefined | null>, resetPage = false) => {
      const next = new URLSearchParams(searchParams);
      Object.entries(patch).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") {
          next.delete(key);
        } else {
          next.set(key, String(value));
        }
      });
      if (resetPage) {
        next.set("page", "0");
      }
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  /** Updates one filter field and resets to the first page. */
  const setFilter = useCallback(
    (key: string, value: string) => {
      patchParams({ [key]: value }, true);
    },
    [patchParams],
  );

  /** Changes the active page index while preserving filters. */
  const setPage = useCallback(
    (page: number) => {
      patchParams({ page });
    },
    [patchParams],
  );

  /** Changes page size and resets to page zero. */
  const setPageSize = useCallback(
    (size: number) => {
      patchParams({ size, page: 0 });
    },
    [patchParams],
  );

  /** Returns filters plus pagination keys for export API payloads. */
  const exportFilterMap = useMemo(
    () => ({
      ...state.filters,
      page: String(state.page),
      size: String(state.size),
      ...(state.sort ? { sort: state.sort } : {}),
    }),
    [state],
  );

  return { state, setFilter, setPage, setPageSize, patchParams, exportFilterMap };
}
