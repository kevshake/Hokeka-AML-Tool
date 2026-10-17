import { useDecisionStatsSummary } from "../../features/api/decisionStats";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  ArcElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";
import { Bar, Doughnut } from "react-chartjs-2";
import { colors, risk, semantic } from "../../theme/tokens";

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Title, Tooltip, Legend);

/**
 * Transaction analytics built from the Edge's decision records and the hourly decision rollup:
 * score bands, decision mix, daily volume and indicator counts. No transaction is pulled here.
 */
export default function TransactionMonitoringAnalytics() {
  const { data, isLoading, isError } = useDecisionStatsSummary(14);

  if (isLoading) return <p className="text-sm text-glass-muted">Loading analytics…</p>;
  if (isError || !data) return <p className="text-sm text-red-300">Decision statistics could not be loaded.</p>;

  const bands = data.scoreBands24h;
  const bandTotal = bands.low + bands.medium + bands.high + bands.critical;
  const indicators = [
    { name: "High score (24h)", description: `Decision records scoring 70 or more`, count: data.highScore24h },
    { name: "Sanctions hits (24h)", description: "Counterparty sanctions matches screened on the Edge", count: data.last24h.sanctionsHit },
    { name: "CTR required (24h)", description: "Cash transactions over the reporting threshold", count: data.last24h.ctrRequired },
    { name: "Fail-closed (24h)", description: "Decisions the Edge took in fail-closed mode", count: data.last24h.failClosed },
    { name: "Blocked (24h)", description: "Transactions the Edge blocked", count: data.last24h.block },
  ];

  const bandChart = {
    labels: ["Low (<30)", "Medium (30–59)", "High (60–79)", "Critical (80+)"],
    datasets: [
      {
        label: "Decision records",
        data: [bands.low, bands.medium, bands.high, bands.critical],
        backgroundColor: [semantic.success, semantic.warning, risk.high, semantic.error],
        borderColor: [risk.low, risk.medium, risk.high, risk.critical],
        borderWidth: 2,
      },
    ],
  };
  const dailyChart = {
    labels: data.daily.map((d) => d.label),
    datasets: [
      { label: "Allow", data: data.daily.map((d) => d.allow), backgroundColor: semantic.success },
      { label: "Alert / Hold", data: data.daily.map((d) => d.alert + d.hold), backgroundColor: semantic.warning },
      { label: "Block", data: data.daily.map((d) => d.block), backgroundColor: semantic.error },
    ],
  };

  return (
    <div className="flex flex-col gap-4">
      <span className="hokeka-section-label">Analytics</span>
      <h3 className="mt-1 font-display text-lg font-semibold tracking-tight text-ink">Transaction Analytics</h3>
      <p className="text-xs text-glass-muted">Built from the decision records your Edge reports. Transaction details stay on the Edge.</p>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        {indicators.map((indicator) => (
          <div key={indicator.name} className="hokeka-glass-card rounded-xl p-4" title={indicator.description}>
            <p className="mb-1 text-sm text-glass-muted">{indicator.name}</p>
            <p className="text-xl font-bold tabular-nums text-ink">{indicator.count.toLocaleString()}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="hokeka-glass-card rounded-xl p-4">
          <h4 className="mb-3 text-sm font-semibold text-ink">Score distribution (24h)</h4>
          {bandTotal > 0 ? (
            <div className="h-[300px]">
              <Doughnut
                data={bandChart}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: { legend: { position: "bottom" as const, labels: { color: colors.muted, padding: 15 } } },
                }}
              />
            </div>
          ) : (
            <div className="flex h-[300px] items-center justify-center text-sm text-glass-muted">No decision records in the last 24 hours</div>
          )}
        </div>
        <div className="hokeka-glass-card rounded-xl p-4">
          <h4 className="mb-3 text-sm font-semibold text-ink">Decisions per day (14 days)</h4>
          <div className="h-[300px]">
            <Bar
              data={dailyChart}
              options={{
                responsive: true,
                maintainAspectRatio: false,
                scales: { x: { stacked: true, ticks: { color: colors.muted } }, y: { stacked: true, ticks: { color: colors.muted } } },
                plugins: { legend: { position: "bottom" as const, labels: { color: colors.muted } } },
              }}
            />
          </div>
        </div>
      </div>

      <div className="hokeka-glass-card rounded-xl p-4">
        <h4 className="mb-3 text-sm font-semibold text-ink">Merchants with the most flagged decisions (14 days)</h4>
        {data.topMerchants.length ? (
          <table className="hokeka-table">
            <thead><tr><th>#</th><th>Merchant</th><th>Decisions</th><th>Flagged</th><th>Blocked</th><th>Flag rate</th></tr></thead>
            <tbody>
              {data.topMerchants.map((m) => (
                <tr key={m.merchantId}>
                  <td>{m.rank}</td>
                  <td className="font-mono text-xs">{m.merchantId}</td>
                  <td className="tabular-nums">{m.records.toLocaleString()}</td>
                  <td className="tabular-nums">{m.flagged.toLocaleString()}</td>
                  <td className="tabular-nums">{m.blocked.toLocaleString()}</td>
                  <td className="tabular-nums">{m.flaggedRate.toFixed(1)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <p className="text-sm text-glass-muted">No flagged decisions in this period</p>
        )}
      </div>
    </div>
  );
}
