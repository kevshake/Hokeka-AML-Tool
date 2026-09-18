import type { ReactNode } from 'react'
import { cn } from '../../lib/utils'

export interface DashboardPanelProps {
  children: ReactNode
  className?: string
  padding?: 'none' | 'sm' | 'md'
  flat?: boolean
  muted?: boolean
  as?: 'section' | 'div' | 'aside'
  'aria-labelledby'?: string
}

const pad = {
  none: '',
  sm: 'p-3',
  md: 'p-4',
} as const

/** Thin-border enterprise surface for dashboard widgets. */
export default function DashboardPanel({
  children,
  className,
  padding = 'md',
  flat = false,
  muted = false,
  as: Tag = 'section',
  'aria-labelledby': labelledBy,
}: DashboardPanelProps) {
  return (
    <Tag
      aria-labelledby={labelledBy}
      className={cn(
        'db-panel flex min-h-0 flex-col',
        flat && 'db-panel--flat',
        muted && 'db-panel--muted',
        pad[padding],
        className,
      )}
    >
      {children}
    </Tag>
  )
}
