import { useTransactionStats } from "../../features/api/queries";

export default function TransactionMonitoringReports() {
  const { data: stats, isLoading } = useTransactionStats();

  const totalCount      = stats?.totalCount      ?? 0;
  const approvedCount   = stats?.approvedCount   ?? 0;
  const declinedCount   = stats?.declinedCount   ?? 0;
  const manualCount     = stats?.manualReviewCount ?? 0;
  const highRiskCount   = stats?.highRiskCount   ?? 0;
  const mediumRiskCount = stats?.mediumRiskCount ?? 0;
  const lowRiskCount    = stats?.lowRiskCount    ?? 0;
  const totalAmountCents   = stats?.totalAmountCents   ?? 0;
  const averageAmountCents = stats?.averageAmountCents ?? 0;
  const fraudAlertCount    = stats?.fraudAlertCount    ?? 0;

  const declineRate = totalCount > 0 ? ((declinedCount / totalCount) * 100).toFixed(2) : "0.00";
  const manualRate  = totalCount > 0 ? ((manualCount  / totalCount) * 100).toFixed(2) : "0.00";

  const fmtAmount = (cents: number) => "$" + (cents / 100).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

  return (
    <div className="flex flex-col gap-4">
      <h3 className="text-lg font-semibold text-white">Transaction Monitoring Reports</h3>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label="Total Transactions" value={totalCount.toLocaleString()} />
        <StatCard label="Approved" value={approvedCount.toLocaleString()} color="text-emerald-400" />
        <StatCard label="Declined" value={declinedCount.toLocaleString()} color="text-red-400" />
        <StatCard label="Manual Review" value={manualCount.toLocaleString()} color="text-amber-400" />
        <StatCard label="Total Volume" value={fmtAmount(totalAmountCents)} />
        <StatCard label="Avg Transaction" value={fmtAmount(averageAmountCents)} />
        <StatCard label="High-Risk" value={highRiskCount.toLocaleString()} color="text-red-400" />
        <StatCard label="Fraud Alerts" value={fraudAlertCount.toLocaleString()} color="text-red-400" />
      </div>

      <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
        <h4 className="mb-3 text-sm font-semibold text-white">Summary Statistics</h4>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Decline Rate</p>
            <p className="mt-1 text-lg font-bold text-white">{declineRate}%</p>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Manual Review Rate</p>
            <p className="mt-1 text-lg font-bold text-white">{manualRate}%</p>
          </div>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-glass-muted">Risk Breakdown (H / M / L)</p>
            <p className="mt-1 text-lg font-bold text-white">{highRiskCount.toLocaleString()} / {mediumRiskCount.toLocaleString()} / {lowRiskCount.toLocaleString()}</p>
          </div>
        </div>
      </div>

      {isLoading && <p className="text-sm text-glass-muted">Loading report data...</p>}
    </div>
  );
}

function StatCard({ label, value, color = "text-white" }: { label: string; value: string; color?: string }) {
  return (
    <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
      <p className="mb-1 text-[11px] font-semibold uppercase tracking-wider text-glass-muted">{label}</p>
      <p className={`text-xl font-bold ${color}`}>{value}</p>
    </div>
  );
}