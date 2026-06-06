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
import KpiCard from '../../components/kpi/KpiCard'
import {
  useDashboardStats,
  useSanctionsStatus,
  useTransactionVolume,
} from '../../hooks/useDashboard'

// Tiny stub sparklines (purely decorative — KPI surface trends visually only).
const SPARK = {
  blue: [12, 18, 14, 22, 26, 20, 31],
  red: [8, 14, 22, 18, 26, 30, 36],
  amber: [10, 12, 18, 22, 19, 24, 28],
  green: [22, 18, 24, 16, 19, 14, 12],
}

function trendOf(delta: number | undefined) {
  const v = delta ?? 0
  return {
    value: Math.abs(Math.round(v * 10) / 10),
    direction: v > 0 ? ('up' as const) : v < 0 ? ('down' as const) : ('flat' as const),
  }
}

export default function DashboardPage() {
  const stats = useDashboardStats()
  const sanctions = useSanctionsStatus()
  const volume = useTransactionVolume(7)

  const totalToday =
    volume.data?.data && volume.data.data.length > 0
      ? volume.data.data[volume.data.data.length - 1]
      : undefined

  const trends = stats.data?.trends

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
      className="grid grid-cols-12 gap-5 pt-2"
    >
      {/* KPI ROW */}
      <div className="col-span-12 grid grid-cols-12 gap-5">
        <div className="col-span-2">
          <KpiCard
            title="Total Transactions"
            subtitle="Today"
            value={totalToday !== undefined ? totalToday.toLocaleString() : undefined}
            icon={ArrowLeftRight}
            iconBg="#1F6FEB"
            trend={trendOf(trends?.totalTransactionsDelta)}
            sparklineData={SPARK.blue}
            sparklineColor="#1F6FEB"
            loading={volume.isLoading}
            error={!!volume.error}
          />
        </div>
        <div className="col-span-2">
          <KpiCard
            title="Flagged Transactions"
            subtitle="Today"
            value={stats.data?.flaggedToday}
            icon={Flag}
            iconBg="#DC2626"
            trend={trendOf(trends?.flaggedDelta)}
            sparklineData={SPARK.red}
            sparklineColor="#DC2626"
            loading={stats.isLoading}
            error={!!stats.error}
          />
        </div>
        <div className="col-span-2">
          <KpiCard
            title="Open Investigations"
            subtitle="Total"
            value={stats.data?.openCases}
            icon={Folder}
            iconBg="#F59E0B"
            trend={trendOf(trends?.openCasesDelta)}
            sparklineData={SPARK.amber}
            sparklineColor="#F59E0B"
            loading={stats.isLoading}
            error={!!stats.error}
          />
        </div>
        <div className="col-span-2">
          <KpiCard
            title="High Risk Customers"
            subtitle="Total"
            value={stats.data?.highRiskCustomerCount}
            icon={User}
            iconBg="#EC4899"
            trend={trendOf(trends?.highRiskCustomersDelta)}
            sparklineData={SPARK.red}
            sparklineColor="#DC2626"
            loading={stats.isLoading}
            error={!!stats.error}
          />
        </div>
        <div className="col-span-2">
          <KpiCard
            title="Screening Matches"
            subtitle="Today"
            value={sanctions.data?.hitsFound}
            icon={ShieldCheck}
            iconBg="#00A86B"
            trend={trendOf(trends?.screeningMatchesDelta)}
            sparklineData={SPARK.green}
            sparklineColor="#00A86B"
            loading={sanctions.isLoading}
            error={!!sanctions.error}
          />
        </div>
        <div className="col-span-2">
          <KpiCard
            title="Compliance Health"
            subtitle="Score"
            value={
              stats.data?.complianceHealthScore !== undefined
                ? `${stats.data.complianceHealthScore}`
                : undefined
            }
            icon={BadgeCheck}
            iconBg="#1F6FEB"
            trend={trendOf(trends?.complianceHealthDelta)}
            sparklineData={SPARK.blue}
            sparklineColor="#1F6FEB"
            loading={stats.isLoading}
            error={!!stats.error}
          />
        </div>
      </div>

      {/* MAIN GRID: left col-span-7 (Risk + Heatmap), right col-span-5 (Alerts + Cases) */}
      <div className="col-span-7 flex flex-col gap-5">
        <RiskGauge />
        <RiskHeatmap />
      </div>
      <div className="col-span-5 flex flex-col gap-5">
        <LiveAlertQueue />
        <InvestigationCases />
      </div>

      {/* BOTTOM ROW — 4 widgets */}
      <div className="col-span-3">
        <AlertTrends />
      </div>
      <div className="col-span-3">
        <ScreeningResults />
      </div>
      <div className="col-span-3">
        <TopRiskMerchants />
      </div>
      <div className="col-span-3">
        <ComplianceHealth />
      </div>
    </motion.div>
  )
}
