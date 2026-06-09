import { motion } from 'framer-motion'
import {
  ArrowLeftRight,
  BadgeCheck,
  Flag,
  Folder,
  ShieldCheck,
  User,
} from 'lucide-react'
import ScreeningResults from '../../components/Alerts/ScreeningResults'
import LiveAlertQueue from '../../components/Alerts/LiveAlertQueue'
import TopRiskMerchants from '../../components/Alerts/TopRiskMerchants'
import InvestigationCases from '../../components/Cases/InvestigationCases'
import AlertTrends from '../../components/charts/AlertTrends'
import RiskGauge from '../../components/charts/RiskGauge'
import RiskHeatmap from '../../components/charts/RiskHeatmap'
import ComplianceHealth from '../../components/compliance/ComplianceHealth'
import InsightsPanel from '../../components/insights/InsightsPanel'
import KpiCard from '../../components/kpi/KpiCard'
import {
  useAlertTrends,
  useCaseTrends,
  useDashboardStats,
  useHighRiskTrends,
  useSanctionsStatus,
  useScreeningMatchTrends,
  useTransactionVolume,
} from '../../hooks/useDashboard'

function trendOf(delta: number | undefined) {
  const v = delta ?? 0
  return {
    value: Math.abs(Math.round(v * 10) / 10),
    direction: v > 0 ? ('up' as const) : v < 0 ? ('down' as const) : ('flat' as const),
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

export default function DashboardPage() {
  const stats = useDashboardStats()
  const sanctions = useSanctionsStatus()
  const volume = useTransactionVolume(7)
  const alertTrends = useAlertTrends(7)
  const caseTrends = useCaseTrends(7)
  const screeningTrends = useScreeningMatchTrends(7)
  const highRiskTrends = useHighRiskTrends(7)

  const volumeSeries = volume.data?.data ?? []
  const monitoredToday =
    stats.data?.transactionsMonitoredToday ??
    (volumeSeries.length > 0 ? volumeSeries[volumeSeries.length - 1] : 0)

  const trends = stats.data?.trends

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
      className="flex h-full max-h-full min-h-0 gap-3 overflow-hidden"
    >
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-2 overflow-hidden">
        <div className="grid flex-shrink-0 grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-3 2xl:grid-cols-6">
          <KpiCard
            title="Transactions Monitored"
            subtitle="Today"
            value={formatKpi(monitoredToday, stats.isLoading && volume.isLoading)}
            icon={ArrowLeftRight}
            iconBg="#7B2332"
            glowVariant="burgundy"
            trend={trendOf(trends?.totalTransactionsDelta)}
            sparklineData={sparkSeries(volumeSeries)}
            sparklineColor="#7B2332"
            loading={stats.isLoading || volume.isLoading}
            error={!!stats.error || !!volume.error}
          />
          <KpiCard
            title="Risk Alerts Generated"
            subtitle="Today"
            value={formatKpi(stats.data?.flaggedToday, stats.isLoading)}
            icon={Flag}
            iconBg="#DC2626"
            glowVariant="red"
            trend={trendOf(trends?.flaggedDelta)}
            sparklineData={sparkSeries(alertTrends.data?.data)}
            sparklineColor="#DC2626"
            loading={stats.isLoading || alertTrends.isLoading}
            error={!!stats.error || !!alertTrends.error}
          />
          <KpiCard
            title="Active Cases"
            subtitle="Total"
            value={formatKpi(stats.data?.openCases, stats.isLoading)}
            icon={Folder}
            iconBg="#F59E0B"
            glowVariant="amber"
            trend={trendOf(trends?.openCasesDelta)}
            sparklineData={sparkSeries(caseTrends.data?.data)}
            sparklineColor="#F59E0B"
            loading={stats.isLoading || caseTrends.isLoading}
            error={!!stats.error || !!caseTrends.error}
          />
          <KpiCard
            title="High Risk Customers"
            subtitle="Total"
            value={formatKpi(stats.data?.highRiskCustomerCount, stats.isLoading)}
            icon={User}
            iconBg="#5A1823"
            glowVariant="red"
            trend={trendOf(trends?.highRiskCustomersDelta)}
            sparklineData={sparkSeries(highRiskTrends.data?.data)}
            sparklineColor="#DC2626"
            loading={stats.isLoading || highRiskTrends.isLoading}
            error={!!stats.error || !!highRiskTrends.error}
          />
          <KpiCard
            title="Watchlist Matches"
            subtitle="Today"
            value={formatKpi(sanctions.data?.hitsFound, sanctions.isLoading)}
            icon={ShieldCheck}
            iconBg="#22C55E"
            glowVariant="green"
            trend={trendOf(trends?.screeningMatchesDelta)}
            sparklineData={sparkSeries(screeningTrends.data?.data)}
            sparklineColor="#22C55E"
            loading={sanctions.isLoading || screeningTrends.isLoading}
            error={!!sanctions.error || !!screeningTrends.error}
          />
          <KpiCard
            title="Compliance Health"
            subtitle="Score"
            value={
              stats.isLoading
                ? undefined
                : stats.data?.complianceHealthScore !== undefined
                  ? `${stats.data.complianceHealthScore}%`
                  : '0%'
            }
            icon={BadgeCheck}
            iconBg="#7B2332"
            glowVariant="gold"
            trend={trendOf(trends?.complianceHealthDelta)}
            loading={stats.isLoading}
            error={!!stats.error}
          />
        </div>

        <div className="grid min-h-0 flex-[1.05] grid-cols-1 gap-2 lg:grid-cols-12 lg:grid-rows-1">
          <div className="h-full min-h-0 lg:col-span-5">
            <RiskGauge />
          </div>
          <div className="h-full min-h-0 lg:col-span-7">
            <LiveAlertQueue />
          </div>
        </div>

        <div className="grid min-h-0 flex-[1.1] grid-cols-1 gap-2 lg:grid-cols-12 lg:grid-rows-1">
          <div className="h-full min-h-0 lg:col-span-7">
            <RiskHeatmap />
          </div>
          <div className="h-full min-h-0 lg:col-span-5">
            <InvestigationCases />
          </div>
        </div>

        <div className="grid flex-shrink-0 grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-4 xl:h-[148px]">
          <AlertTrends />
          <ScreeningResults />
          <TopRiskMerchants />
          <ComplianceHealth />
        </div>
      </div>

      <InsightsPanel />
    </motion.div>
  )
}
