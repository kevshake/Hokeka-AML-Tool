import { ChevronLeft, ChevronRight } from "lucide-react";

interface Props {
  /** Zero-based page index. */
  page: number;
  /** Rows on the current page. */
  rowCount: number;
  rowsPerPage: number;
  nextCursor: string | null | undefined;
  onNext: (cursor: string) => void;
  onPrevious: () => void;
  onRowsPerPageChange: (size: number) => void;
  disabled?: boolean;
}

const SIZES = [25, 50, 100, 200];

/**
 * Previous / next paging for Edge-backed lists. There is no total count or page number jump: the
 * Edge pages by cursor only, and counting would mean scanning every site's ledger.
 */
export default function EdgeCursorPagination({
  page,
  rowCount,
  rowsPerPage,
  nextCursor,
  onNext,
  onPrevious,
  onRowsPerPageChange,
  disabled,
}: Props) {
  const first = page * rowsPerPage + (rowCount > 0 ? 1 : 0);
  const last = page * rowsPerPage + rowCount;
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-hairline px-4 py-2 text-xs text-glass-muted">
      <label className="flex items-center gap-2">
        Rows per page
        <select
          aria-label="Rows per page"
          value={rowsPerPage}
          onChange={(event) => onRowsPerPageChange(Number(event.target.value))}
          className="hokeka-field h-8 py-0"
        >
          {SIZES.map((size) => (
            <option key={size} value={size}>{size}</option>
          ))}
        </select>
      </label>
      <span data-testid="cursor-range">
        Page {page + 1} · rows {first}–{last}
      </span>
      <div className="flex items-center gap-1">
        <button
          type="button"
          aria-label="Previous page"
          disabled={disabled || page === 0}
          onClick={onPrevious}
          className="flex items-center gap-1 rounded px-2 py-1 hover:bg-white/5 disabled:opacity-40"
        >
          <ChevronLeft size={14} /> Previous
        </button>
        <button
          type="button"
          aria-label="Next page"
          disabled={disabled || !nextCursor}
          onClick={() => nextCursor && onNext(nextCursor)}
          className="flex items-center gap-1 rounded px-2 py-1 hover:bg-white/5 disabled:opacity-40"
        >
          Next <ChevronRight size={14} />
        </button>
      </div>
    </div>
  );
}
