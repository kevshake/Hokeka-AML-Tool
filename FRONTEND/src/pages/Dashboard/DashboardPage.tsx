import './dashboard.css'
import { lazy, Suspense, memo } from 'react'
import {
  ArrowLeftRight,
  BadgeCheck,
  Flag,
  Folder,
  ShieldCheck,
  User,
} from 'lucide-react'
import ScreeningResults from '../../components/Alerts/ScreeningResults'
import MonitoringAlertsPanel from '../../components/monitoring/MonitoringAlertsPanel'
import LiveAlertQueue from '../../components/Alerts/LiveAlertQueue'
import TopRiskMerchants from '../../components/Alerts/TopRiskMerchants'
import InvestigationCases from '../../components/Cases/InvestigationCases'
import AlertTrends from '../../components/charts/AlertTrends'
import RiskGauge from '../../components/charts/RiskGauge'
import InsightsPanel from '../../components/insights/InsightsPanel'
import DashboardKpiCard from '../../components/dashboard/DashboardKpiCard'
import ComplianceHealthPanel from '../../components/dashboard/ComplianceHealthPanel'
import {
  useDashboardSparklines,
  useDashboardStats,
  useSanctionsStatus,
} from '../../hooks/useDashboard'

const RiskHeatmap = lazy(() => import('../../components/charts/RiskHeatmap'))

function trendOf(delta: number | undefined, invertSemantic = false) {
  const v = delta ?? 0
  return {
    value: Math.abs(Math.round(v * 10) / 10),
    direction: v > 0 ? ('up' as const) : v < 0 ? ('down' as const) : ('flat' as const),
    invertSemantic,
  }
}

function formatKpi(value: number | undefined, loading: boolean) {
  if (loading) return undefined
  return (value ?? 0).toLocaleString()
}

function sparkSeries(data: number[] | undefined): number[] | undefined {
  if (!data?.length) return undefined
  return data
}

function HeatmapFallback() {
  return (
    <div className="db-panel flex h-full min-h-[220px] items-center p-4" role="status" aria-label="Loading map">
      <div className="db-skel h-full min-h-[180px] w-full" />
    </div>
  )
}

const KpiRow = memo(function KpiRow() {
  const stats = useDashboardStats()
  const sanctions = useSanctionsStatus()
  const sparklines = useDashboardSparklines(7)

  const volumeSeries = sparklines.data?.transactionVolume?.data ?? []
  const monitoredToday =
    stats.data?.transactionsMonitoredToday ??
    (volumeSeries.length > 0 ? volumeSeries[volumeSeries.length - 1] : 0)

  const trends = stats.data?.trends
  const sparkLoading = sparklines.isLoading && !sparklines.data
  const statsLoading = stats.isLoading && !stats.data
  const sanctionsLoading = sanctions.isLoading && !sanctions.data

  return (
    <div
      className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6"
      role="region"
      aria-label="Key performance indicators"
    >
      <DashboardKpiCard
        title="Transactions monitored"
        subtitle="Today"
        value={formatKpi(monitoredToday, statsLoading && sparkLoading)}
        icon={ArrowLeftRight}
        tone="accent"
        trend={trendOf(trends?.totalTransactionsDelta)}
        sparklineData={sparkSeries(volumeSeries)}
        loading={statsLoading || sparkLoading}
        error={!!stats.error || !!sparklines.error}
      />
      <DashboardKpiCard
        title="Risk alerts"
        subtitle="Flagged today"
        value={formatKpi(stats.data?.flaggedToday, statsLoading)}
        icon={Flag}
        tone="danger"
        trend={trendOf(trends?.flaggedDelta, true)}
        sparklineData={sparkSeries(sparklines.data?.alertTrends?.data)}
        loading={statsLoading || sparkLoading}
        error={!!stats.error || !!sparklines.error}
      />
      <DashboardKpiCard
        title="Active cases"
        subtitle="Open workload"
        value={formatKpi(stats.data?.openCases, statsLoading)}
        icon={Folder}
        tone="warning"
        trend={trendOf(trends?.openCasesDelta, true)}
        sparklineData={sparkSeries(sparklines.data?.caseTrends?.data)}
        loading={statsLoading || sparkLoading}
        error={!!stats.error || !!sparklines.error}
      />
      <DashboardKpiCard
        title="High-risk customers"
        subtitle="Merchants"
        value={formatKpi(stats.data?.highRiskCustomerCount, statsLoading)}
        icon={User}
        tone="info"
        trend={trendOf(trends?.highRiskCustomersDelta, true)}
        sparklineData={sparkSeries(sparklines.data?.highRiskTrends?.data)}
        loading={statsLoading || sparkLoading}
        error={!!stats.error || !!sparklines.error}
      />
      <DashboardKpiCard
        title="Watchlist matches"
        subtitle="Today"
        value={formatKpi(sanctions.data?.hitsFound, sanctionsLoading)}
        icon={ShieldCheck}
        tone="success"
        trend={trendOf(trends?.screeningMatchesDelta, true)}
        sparklineData={sparkSeries(sparklines.data?.screeningMatchTrends?.data)}
        loading={sanctionsLoading || sparkLoading}
        error={!!sanctions.error || !!sparklines.error}
      />
      <DashboardKpiCard
        title="Compliance health"
        subtitle="Composite score"
        value={
          statsLoading
            ? undefined
            : stats.data?.complianceHealthScore !== undefined
              ? `${stats.data.complianceHealthScore}%`
              : '0%'
        }
        icon={BadgeCheck}
        tone="accent"
        trend={trendOf(trends?.complianceHealthDelta)}
        loading={statsLoading}
        error={!!stats.error}
      />
    </div>
  )
})

function DashboardHeader() {
  const stats = useDashboardStats()
  const urgent = stats.data?.urgentCases
  const openAlerts = stats.data?.openAlertsCount

  return (
    <header className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div>
        <p className="db-section-label mb-1">Operations</p>
        <h1 className="font-[family-name:var(--db-display)] text-[22px] font-semibold tracking-tight text-[var(--db-text)]">
          Compliance dashboard
        </h1>
        <p className="db-subtitle mt-1 max-w-xl">
          Live monitoring of transactions, alerts, cases, and screening posture.
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-2" aria-live="polite">
        {urgent != null && urgent > 0 ? (
          <span className="db-chip db-chip--danger">{urgent} critical cases</span>
        ) : null}
        {openAlerts != null ? (
          <span className="db-chip">{openAlerts} open alerts</span>
        ) : null}
      </div>
    </header>
  )
}

export default function DashboardPage() {
  return (
    <div className="crm-dashboard -mx-1 min-h-full rounded-[var(--db-radius)] px-1 py-1 sm:px-2">
      <div className="flex min-h-full flex-col gap-4 xl:flex-row xl:items-start">
        <div className="flex min-w-0 flex-1 flex-col gap-4 pb-4">
          <DashboardHeader />
          <KpiRow />

          <section aria-label="Risk and live alerts" className="grid grid-cols-1 gap-4 lg:grid-cols-12">
            <div className="min-h-[260px] lg:col-span-4">
              <RiskGauge />
            </div>
            <div className="min-h-[260px] lg:col-span-8">
              <LiveAlertQueue />
            </div>
          </section>

          <section aria-label="Geography and investigations" className="grid grid-cols-1 gap-4 lg:grid-cols-12">
            <div className="min-h-[280px] lg:col-span-7">
              <Suspense fallback={<HeatmapFallback />}>
                <RiskHeatmap />
              </Suspense>
            </div>
            <div className="flex min-h-[280px] flex-col gap-4 lg:col-span-5">
              <InvestigationCases />
              <ComplianceHealthPanel />
            </div>
          </section>

          <section
            aria-label="Secondary analytics"
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4"
          >
            <AlertTrends />
            <ScreeningResults />
            <TopRiskMerchants />
            <MonitoringAlertsPanel />
          </section>
        </div>

        <InsightsPanel />
      </div>
    </div>
  )
}
