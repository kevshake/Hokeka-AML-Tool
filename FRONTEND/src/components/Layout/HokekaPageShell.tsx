import type { ReactNode } from 'react'
import GlassCard from '../Common/GlassCard'
import { cn } from '../../lib/utils'

interface HokekaPageShellProps {
  title: string
  subtitle?: string
  actions?: ReactNode
  children: ReactNode
  /** Render children without an outer GlassCard wrapper */
  noCard?: boolean
  className?: string
}

export default function HokekaPageShell({
  title,
  subtitle,
  actions,
  children,
  noCard = false,
  className,
}: HokekaPageShellProps) {
  return (
    <div className={cn('flex flex-col gap-5 pt-1', className)}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 className="hokeka-page-title">{title}</h2>
          {subtitle ? <p className="hokeka-page-subtitle">{subtitle}</p> : null}
        </div>
        {actions ? (
          <div className="flex flex-shrink-0 flex-wrap items-center gap-2.5">{actions}</div>
        ) : null}
      </div>
      {noCard ? children : <GlassCard padding="md">{children}</GlassCard>}
    </div>
  )
}
