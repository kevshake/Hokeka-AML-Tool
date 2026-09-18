import {
  useMonitoringRiskDistribution,
  useMonitoringRiskIndicators,
} from "../../features/api/queries";
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
import { Doughnut } from "react-chartjs-2";
import { colors, risk, semantic } from "../../theme/tokens";

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Title, Tooltip, Legend);

export default function TransactionMonitoringAnalytics() {
  const { data: riskDistribution } = useMonitoringRiskDistribution();
  const { data: riskIndicators } = useMonitoringRiskIndicators();

  const riskChartData = riskDistribution
    ? {
        labels: Object.keys(riskDistribution),
        datasets: [
          {
            label: "Risk Distribution",
            data: Object.values(riskDistribution),
            backgroundColor: [semantic.success, semantic.warning, semantic.error],
            borderColor: [risk.low, risk.high, risk.critical],
            borderWidth: 2,
          },
        ],
      }
    : null;

  const indicators = (riskIndicators && Array.isArray(riskIndicators)) ? riskIndicators : [];

  return (
    <div className="flex flex-col gap-4">
      <h3 className="text-lg font-semibold text-white">Transaction Analytics</h3>

      {indicators.length > 0 && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {indicators.slice(0, 4).map((indicator: any, idx: number) => (
            <div key={indicator.id || indicator.name || idx} className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
              <p className="mb-1 text-sm text-glass-muted">{indicator.name || indicator.type || "Risk Indicator"}</p>
              <p className="text-xl font-bold text-white">{indicator.value || indicator.count || 0}</p>
              {indicator.percentage && <p className="text-xs text-glass-muted">{indicator.percentage}%</p>}
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
          <h4 className="mb-3 text-sm font-semibold text-white">Risk Distribution</h4>
          {riskChartData ? (
            <div className="h-[300px]">
              <Doughnut
                data={riskChartData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: { position: "bottom" as const, labels: { color: colors.muted, padding: 15 } },
                  },
                }}
              />
            </div>
          ) : (
            <div className="flex h-[300px] items-center justify-center text-sm text-glass-muted">No risk distribution data available</div>
          )}
        </div>
        <div className="rounded-lg border border-white/10 bg-[var(--surface-2)] p-4">
          <h4 className="mb-3 text-sm font-semibold text-white">Top Risk Indicators</h4>
          {indicators.length > 0 ? (
            <div className="space-y-1">
              {indicators.map((indicator: any, idx: number) => (
                <div key={indicator.id || indicator.name || idx} className="flex items-center justify-between rounded-lg border border-white/5 bg-[var(--surface-2)] p-3">
                  <p className="text-sm text-white/80">{indicator.name || indicator.type || "Indicator"}</p>
                  <p className="text-base font-bold text-burgundy-400">{indicator.value || indicator.count || 0}</p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-glass-muted">No risk indicators available</p>
          )}
        </div>
      </div>
    </div>
  );
}