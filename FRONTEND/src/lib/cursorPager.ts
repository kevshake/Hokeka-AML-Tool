/**
 * Cursor-only paging of the Edge-backed lists (contract section 7.5): the server has no page
 * numbers (a page above 0 without a cursor is refused), so the Console keeps the cursors of the
 * pages it has visited and walks back through them.
 */

/** Cursors of the visited pages; entry 0 is the first page (no cursor). */
export interface CursorPagerState {
  stack: ReadonlyArray<string | null>;
}

/** Paging actions. */
export type CursorPagerAction =
  | { type: "next"; cursor: string }
  | { type: "previous" }
  | { type: "reset" };

/** The first page. */
export const FIRST_PAGE: CursorPagerState = { stack: [null] };

/** Applies one paging action. */
export function cursorPagerReducer(state: CursorPagerState, action: CursorPagerAction): CursorPagerState {
  switch (action.type) {
    case "next":
      return action.cursor ? { stack: [...state.stack, action.cursor] } : state;
    case "previous":
      return state.stack.length > 1 ? { stack: state.stack.slice(0, -1) } : state;
    case "reset":
      return state.stack.length === 1 ? state : FIRST_PAGE;
    default:
      return state;
  }
}

/** Cursor to send for the current page ({@code undefined} on the first page). */
export function currentCursor(state: CursorPagerState): string | undefined {
  return state.stack[state.stack.length - 1] ?? undefined;
}

/** Zero-based index of the current page. */
export function pageIndex(state: CursorPagerState): number {
  return state.stack.length - 1;
}
