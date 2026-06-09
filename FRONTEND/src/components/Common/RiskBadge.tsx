import { cn } from '../../lib/utils'

export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string

const STYLES: Record<string, string> = {
  CRITICAL: 'bg-risk-critical/20 text-risk-critical border-risk-critical/45 shadow-neon-red',
  HIGH: 'bg-risk-high/20 text-risk-high border-risk-high/40',
  MEDIUM: 'bg-risk-medium/20 text-risk-medium border-risk-medium/40',
  LOW: 'bg-risk-low/20 text-risk-low border-risk-low/40',
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
        'inline-flex items-center rounded-md border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide',
        STYLES[key] ?? 'bg-burgundy-850/60 text-white/75 border-glass-border',
        className,
      )}
    >
      {level || '—'}
    </span>
  )
}
