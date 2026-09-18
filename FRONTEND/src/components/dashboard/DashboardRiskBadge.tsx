import { cn } from '../../lib/utils'

export type DashboardRiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string

export default function DashboardRiskBadge({
  level,
  className,
}: {
  level: DashboardRiskLevel
  className?: string
}) {
  const key = (level ?? '').toUpperCase()
  const tone =
    key === 'CRITICAL' || key === 'HIGH'
      ? 'db-risk-badge--high'
      : key === 'MEDIUM'
        ? 'db-risk-badge--medium'
        : key === 'LOW'
          ? 'db-risk-badge--low'
          : 'db-risk-badge--medium'

  return (
    <span className={cn('db-risk-badge', tone, className)}>{level || '—'}</span>
  )
}
