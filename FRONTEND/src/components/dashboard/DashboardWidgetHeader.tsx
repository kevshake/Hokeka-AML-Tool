import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'

export interface DashboardWidgetHeaderProps {
  id?: string
  title: string
  description?: string
  actionLabel?: string
  actionTo?: string
  trailing?: ReactNode
}

export default function DashboardWidgetHeader({
  id,
  title,
  description,
  actionLabel,
  actionTo,
  trailing,
}: DashboardWidgetHeaderProps) {
  return (
    <div className="mb-3 flex items-start justify-between gap-3">
      <div className="min-w-0">
        <h3 id={id} className="db-title">
          {title}
        </h3>
        {description ? <p className="db-subtitle mt-0.5">{description}</p> : null}
      </div>
      <div className="flex flex-shrink-0 items-center gap-2">
        {trailing}
        {actionLabel && actionTo ? (
          <Link to={actionTo} className="db-link">
            {actionLabel} <ArrowRight size={12} aria-hidden />
          </Link>
        ) : null}
      </div>
    </div>
  )
}
