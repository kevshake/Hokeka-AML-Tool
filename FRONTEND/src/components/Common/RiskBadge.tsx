import { cn } from '../../lib/utils'

export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string

/*
 * Opaque grounds, not alpha tints: an alpha tint composites against the row
 * underneath, so a hovered row would lighten the badge and drop the text below
 * AA. Every pair here is >= 4.72:1 regardless of what it sits on.
 */
const STYLES: Record<string, string> = {
  CRITICAL: 'bg-risk-critical-soft text-risk-critical border-risk-critical/40',
  HIGH: 'bg-risk-high-soft text-risk-high border-risk-high/40',
  MEDIUM: 'bg-risk-medium-soft text-risk-medium border-risk-medium/40',
  LOW: 'bg-risk-low-soft text-risk-low border-risk-low/40',
}

export interface RiskBadgeProps {
  level: RiskLevel
  className?: string
}

export default function RiskBadge({ level, className }: RiskBadgeProps) {
  const key = (level ?? '').toUpperCase()
  return (
    <span
      className={cn(
        'inline-flex items-center rounded border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider',
        STYLES[key] ?? 'bg-risk-unknown-soft text-risk-unknown border-hairline-strong',
        className,
      )}
    >
      {level || '—'}
    </span>
  )
}
