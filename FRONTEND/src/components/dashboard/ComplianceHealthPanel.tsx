import DashboardPanel from './DashboardPanel'
import DashboardWidgetHeader from './DashboardWidgetHeader'
import { DashboardEmpty, DashboardError, DashboardLoading } from './DashboardState'
import { useComplianceHealth, type ComplianceHealth } from '../../hooks/useDashboard'

const ROWS: { key: keyof ComplianceHealth; label: string; hint: string }[] = [
  { key: 'kycCompletion', label: 'KYC completion', hint: 'Onboarded merchants with complete KYC' },
  { key: 'cddReviews', label: 'CDD reviews', hint: 'Customer due diligence coverage' },
  { key: 'eddReviews', label: 'EDD reviews', hint: 'Enhanced diligence on high-risk' },
  { key: 'sarFilingSla', label: 'SAR filing SLA', hint: 'Filed within regulatory window' },
]

function barColor(score: number) {
  if (score >= 80) return 'var(--db-success)'
  if (score >= 55) return 'var(--db-warning)'
  return 'var(--db-danger)'
}

/** Breakdown behind the composite compliance health KPI. */
export default function ComplianceHealthPanel() {
  const { data, isLoading, error } = useComplianceHealth()

  return (
    <DashboardPanel aria-labelledby="db-compliance-health" padding="md" className="min-h-0">
      <DashboardWidgetHeader
        id="db-compliance-health"
        title="Compliance posture"
        description="Component scores behind the composite health KPI"
        actionLabel="Calendar"
        actionTo="/compliance-calendar"
      />

      {isLoading && !data ? (
        <DashboardLoading rows={4} />
      ) : error ? (
        <DashboardError message="Compliance health metrics unavailable." />
      ) : !data ? (
        <DashboardEmpty message="No compliance health data yet." />
      ) : (
        <ul className="space-y-2.5">
          {ROWS.map((row) => {
            const score = Number(data[row.key] ?? 0)
            return (
              <li key={row.key}>
                <div className="mb-1 flex items-center justify-between gap-2 text-xs">
                  <div className="min-w-0">
                    <p className="font-medium text-[var(--db-text)]">{row.label}</p>
                    <p className="truncate text-[10px] text-[var(--db-text-muted)]">{row.hint}</p>
                  </div>
                  <span className="tabular-nums font-semibold text-[var(--db-text)]">{score}%</span>
                </div>
                <div
                  className="h-1.5 overflow-hidden rounded-full bg-[#eceef2]"
                  role="progressbar"
                  aria-valuenow={score}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label={row.label}
                >
                  <div
                    className="h-full rounded-full transition-[width]"
                    style={{ width: `${Math.min(100, Math.max(0, score))}%`, backgroundColor: barColor(score) }}
                  />
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </DashboardPanel>
  )
}
