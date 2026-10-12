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
    <div className="mb-3">
      <div className="flex flex-nowrap items-center justify-between gap-2">
        <h3 id={id} className="db-title min-w-0 flex-1 truncate">
          {title}
        </h3>
        <div className="flex flex-shrink-0 items-center gap-1.5">
          {trailing}
          {actionLabel && actionTo ? (
            <Link to={actionTo} className="db-link whitespace-nowrap text-[11px]">
              {actionLabel} <ArrowRight size={12} aria-hidden />
            </Link>
          ) : null}
        </div>
      </div>
      {description ? <p className="db-subtitle mt-1 line-clamp-2">{description}</p> : null}
    </div>
  )
}
