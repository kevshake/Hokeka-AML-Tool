import { Inbox } from 'lucide-react'
import { type ReactNode } from 'react'
import { cn } from '../../lib/utils'

export interface Column<T> {
  key: string
  label: string
  sortable?: boolean
  render: (row: T) => ReactNode
  className?: string
}

interface TwTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  keyExtractor: (row: T) => string | number
  loading?: boolean
  emptyMessage?: string
  maxHeight?: string
}

export default function TwTable<T>({
  columns,
  rows,
  keyExtractor,
  loading,
  emptyMessage = 'No records found',
  maxHeight = 'calc(100vh - 320px)',
}: TwTableProps<T>) {
  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-burgundy-700 border-t-transparent" />
        <span className="ml-3 text-sm text-glass-muted">Loading...</span>
      </div>
    )
  }

  if (!rows.length) {
    return (
      <div className="hokeka-empty-state mx-1 my-2">
        <div className="hokeka-empty-state__icon" aria-hidden>
          <Inbox size={22} strokeWidth={1.75} />
        </div>
        <p className="hokeka-empty-state__title">Nothing here yet</p>
        <p className="hokeka-empty-state__body">{emptyMessage}</p>
      </div>
    )
  }

  return (
    <div className="hokeka-table-wrap" style={{ maxHeight }}>
      <table className="hokeka-table">
        <thead className="sticky top-0 z-10">
          <tr>
            {columns.map((col) => (
              <th key={col.key} className={cn(col.className)}>
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={keyExtractor(row)}>
              {columns.map((col) => (
                <td key={col.key} className={cn(col.className)}>
                  {col.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
