import { useCallback, useState } from "react";
import { FIRST_PAGE, currentCursor, cursorPagerReducer, pageIndex, type CursorPagerAction, type CursorPagerState } from "../lib/cursorPager";

/**
 * Cursor stack for an Edge-backed list. {@code resetKey} identifies the filters: when it changes
 * the list goes back to its first page in the same render (a cursor is bound to the parameters it
 * was minted for, so an old cursor is never sent with new filters).
 */
export function useCursorPager(resetKey: string) {
  const [held, setHeld] = useState<{ key: string; pager: CursorPagerState }>({ key: resetKey, pager: FIRST_PAGE });
  let current = held;
  if (held.key !== resetKey) {
    current = { key: resetKey, pager: FIRST_PAGE };
    setHeld(current);
  }

  /** Applies a paging action to the stack of the current filters. */
  const dispatch = useCallback(
    (action: CursorPagerAction) => setHeld((prev) => ({ key: prev.key, pager: cursorPagerReducer(prev.pager, action) })),
    [],
  );
  /** Moves to the page after the current one. */
  const next = useCallback((cursor: string) => dispatch({ type: "next", cursor }), [dispatch]);
  /** Moves back one page. */
  const previous = useCallback(() => dispatch({ type: "previous" }), [dispatch]);
  /** Returns to the first page. */
  const reset = useCallback(() => dispatch({ type: "reset" }), [dispatch]);

  return { cursor: currentCursor(current.pager), page: pageIndex(current.pager), next, previous, reset };
}
