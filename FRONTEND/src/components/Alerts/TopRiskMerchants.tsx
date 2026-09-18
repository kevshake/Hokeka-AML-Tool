import { Link } from 'react-router-dom'
import DashboardPanel from '../dashboard/DashboardPanel'
import DashboardWidgetHeader from '../dashboard/DashboardWidgetHeader'
import DashboardRiskBadge from '../dashboard/DashboardRiskBadge'
import { DashboardEmpty, DashboardError, DashboardLoading } from '../dashboard/DashboardState'
import { useTopRiskMerchants } from '../../hooks/useDashboard'

/** Answers: Which merchants currently carry the highest risk scores? */
export default function TopRiskMerchants() {
  const { data, isLoading, error } = useTopRiskMerchants(5)
  const merchants = data ?? []

  return (
    <DashboardPanel aria-labelledby="db-top-merchants" className="min-h-[200px]">
      <DashboardWidgetHeader
        id="db-top-merchants"
        title="Highest-risk merchants"
        description="Ranked by stored risk score"
        actionLabel="Merchants"
        actionTo="/merchants"
      />

      {isLoading && !data ? (
        <DashboardLoading rows={5} />
      ) : error ? (
        <DashboardError message="Merchant risk ranking unavailable." />
      ) : merchants.length === 0 ? (
        <DashboardEmpty message="No merchants available to rank." />
      ) : (
        <ol className="flex min-h-0 flex-1 flex-col justify-between gap-1">
          {merchants.map((m) => (
            <li
              key={m.merchantId ?? m.rank}
              className="flex items-center justify-between gap-2 text-xs"
            >
              <div className="flex min-w-0 items-center gap-2">
                <span className="w-4 flex-shrink-0 tabular-nums text-[var(--db-text-muted)]">
                  {m.rank}.
                </span>
                <Link
                  to="/merchants"
                  className="truncate font-medium text-[var(--db-text)] hover:text-[var(--db-accent)]"
                >
                  {m.name ?? `Merchant #${m.merchantId ?? '—'}`}
                </Link>
              </div>
              <div className="flex flex-shrink-0 items-center gap-2">
                {m.riskScore != null && (
                  <span className="tabular-nums font-semibold text-[var(--db-text)]">
                    {Math.round(m.riskScore)}
                  </span>
                )}
                <DashboardRiskBadge level={m.riskLevel} />
              </div>
            </li>
          ))}
        </ol>
      )}
    </DashboardPanel>
  )
}
