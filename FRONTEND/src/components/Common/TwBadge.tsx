import { cn } from '../../lib/utils'
import type { ReactNode } from 'react'

interface TwBadgeProps {
  children: ReactNode
  variant?: 'default' | 'success' | 'warning' | 'danger' | 'info' | 'gold'
  size?: 'sm' | 'md'
  className?: string
}

const VARIANT_CLASSES = {
  default: 'bg-neutral-soft text-ink-muted border-hairline-strong',
  success: 'bg-success-soft text-success border-success/35',
  warning: 'bg-warning-soft text-warning border-warning/35',
  danger: 'bg-danger-soft text-danger border-danger/35',
  info: 'bg-info-soft text-info border-info/35',
  gold: 'bg-[var(--brand-soft)] text-gold border-gold/35',
}

export default function TwBadge({
  children,
  variant = 'default',
  size = 'sm',
  className,
}: TwBadgeProps) {
  return (
    <span
      className={cn(
        'hokeka-badge',
        size === 'sm' ? 'text-[11px]' : 'text-xs px-3 py-1',
        VARIANT_CLASSES[variant],
        className,
      )}
    >
      {children}
    </span>
  )
}
