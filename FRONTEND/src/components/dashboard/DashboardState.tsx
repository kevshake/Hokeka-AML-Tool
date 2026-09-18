import { AlertCircle, Inbox } from 'lucide-react'

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

export function DashboardEmpty({ message = 'No data available' }: { message?: string }) {
  return (
    <div className="db-empty flex-col gap-1.5">
      <Inbox size={16} aria-hidden className="opacity-50" />
      <span>{message}</span>
    </div>
  )
}
