import { AlertCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

export function DashboardLoading({ rows = 4, className = '' }: { rows?: number; className?: string }) {
  return (
    <div className={`space-y-2 ${className}`} role="status" aria-live="polite" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="db-skel h-8 w-full" />
      ))}
    </div>
  )
}

export function DashboardError({ message = 'Could not load data. Try refreshing the page.' }: { message?: string }) {
  return (
    <div className="db-error gap-2" role="alert">
      <AlertCircle size={14} aria-hidden />
      <span>{message}</span>
    </div>
  )
}

export interface DashboardEmptyProps {
  message?: string
  hint?: string
  actionLabel?: string
  actionTo?: string
  compact?: boolean
}

/** Compact empty state for dashboard widgets (optional single-line hint + CTA). */
export function DashboardEmpty({
  message = 'No data available',
  hint,
  actionLabel,
  actionTo,
  compact = true,
}: DashboardEmptyProps) {
  return (
    <div
      className={
        compact
          ? 'db-empty-compact flex flex-col items-start gap-2 py-3 text-left'
          : 'db-empty flex-col gap-1.5'
      }
    >
      <p className="text-xs text-[var(--db-text-secondary)]">{message}</p>
      {hint ? <p className="text-[11px] text-[var(--db-text-muted)]">{hint}</p> : null}
      {actionLabel && actionTo ? (
        <Link to={actionTo} className="db-link text-[11px]">
          {actionLabel}
        </Link>
      ) : null}
    </div>
  )
}
