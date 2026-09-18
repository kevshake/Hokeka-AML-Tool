import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react'
import { cn } from '../../lib/utils'

interface TwPaginationProps {
  page: number
  totalPages: number
  totalCount: number
  rowsPerPage: number
  onPageChange: (page: number) => void
  onRowsPerPageChange: (rowsPerPage: number) => void
  rowsPerPageOptions?: number[]
}

function getDisplayRange(page: number, rowsPerPage: number, totalCount: number) {
  const start = page * rowsPerPage + 1
  const end = Math.min((page + 1) * rowsPerPage, totalCount)
  return { start, end }
}

const ROWS_OPTIONS = [10, 25, 50, 100]

export default function TwPagination({
  page,
  totalPages,
  totalCount,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  rowsPerPageOptions = ROWS_OPTIONS,
}: TwPaginationProps) {
  if (totalCount === 0) return null

  const { start, end } = getDisplayRange(page, rowsPerPage, totalCount)

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-hairline bg-burgundy-900/90 px-4 py-3">
      <div className="flex items-center gap-2 text-xs text-glass-muted">
        <span>
          {start}–{end} of {totalCount}
        </span>
        <span className="text-ink-subtle">|</span>
        <span className="flex items-center gap-1.5">
          Rows:
          <select
            value={rowsPerPage}
            onChange={(e) => onRowsPerPageChange(Number(e.target.value))}
            className="hokeka-field !w-auto !rounded-md !px-1.5 !py-0.5 !text-xs"
          >
            {rowsPerPageOptions.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </span>
      </div>

      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange(0)}
          disabled={page === 0}
          className={cn(
            'rounded-lg p-1.5 transition-colors',
            page === 0
              ? 'cursor-not-allowed text-ink-subtle'
              : 'text-glass-muted hover:bg-burgundy-800 hover:text-ink',
          )}
        >
          <ChevronsLeft size={16} />
        </button>
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          className={cn(
            'rounded-lg p-1.5 transition-colors',
            page === 0
              ? 'cursor-not-allowed text-ink-subtle'
              : 'text-glass-muted hover:bg-burgundy-800 hover:text-ink',
          )}
        >
          <ChevronLeft size={16} />
        </button>

        <span className="min-w-[4.5rem] text-center text-xs font-medium text-ink-muted">
          Page {page + 1} of {totalPages || 1}
        </span>

        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className={cn(
            'rounded-lg p-1.5 transition-colors',
            page >= totalPages - 1
              ? 'cursor-not-allowed text-ink-subtle'
              : 'text-glass-muted hover:bg-burgundy-800 hover:text-ink',
          )}
        >
          <ChevronRight size={16} />
        </button>
        <button
          type="button"
          onClick={() => onPageChange(totalPages - 1)}
          disabled={page >= totalPages - 1}
          className={cn(
            'rounded-lg p-1.5 transition-colors',
            page >= totalPages - 1
              ? 'cursor-not-allowed text-ink-subtle'
              : 'text-glass-muted hover:bg-burgundy-800 hover:text-ink',
          )}
        >
          <ChevronsRight size={16} />
        </button>
      </div>
    </div>
  )
}
